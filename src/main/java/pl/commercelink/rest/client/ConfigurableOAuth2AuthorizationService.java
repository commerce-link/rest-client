package pl.commercelink.rest.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurableOAuth2AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableOAuth2AuthorizationService.class);

    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";

    static final Duration RENEWAL_COOLDOWN = Duration.ofSeconds(60);

    /**
     * Last renewal per store and token name. Static on purpose: consumers build a new service instance for
     * every provider call, so an instance field would never throttle anything.
     */
    private static final Map<String, Instant> LAST_RENEWAL = new ConcurrentHashMap<>();

    /**
     * One lock per store and token name, shared by every instance of this class in the JVM: refresh tokens may be
     * single-use (Allegro), so two refreshes with the same token would make the second one fail with invalid_grant.
     * It does not serialize refreshes across processes; that case is handled by the re-read after invalid_grant.
     */
    private static final Map<String, Object> REFRESH_LOCKS = new ConcurrentHashMap<>();

    private final OAuth2CredentialStore credentialStore;
    private final OAuth2TokenStore tokenStore;
    private final JsonHttpClient httpClient;
    private final Clock clock;
    private final String tokenName;
    private final String authorizationEndpoint;
    private final String refreshTokenEndpoint;
    private final long refreshTokenExpirationInSeconds;
    private final Consumer<String> connectionLostHandler;

    public ConfigurableOAuth2AuthorizationService(
            OAuth2CredentialStore credentialStore,
            OAuth2TokenStore tokenStore,
            String tokenName,
            String authorizationEndpoint,
            String refreshTokenEndpoint,
            long refreshTokenExpirationInSeconds,
            Consumer<String> connectionLostHandler) {
        this(credentialStore, tokenStore, new JsonHttpClient(), Clock.systemUTC(), tokenName, authorizationEndpoint,
                refreshTokenEndpoint, refreshTokenExpirationInSeconds, connectionLostHandler);
    }

    ConfigurableOAuth2AuthorizationService(
            OAuth2CredentialStore credentialStore,
            OAuth2TokenStore tokenStore,
            JsonHttpClient httpClient,
            String tokenName,
            String authorizationEndpoint,
            String refreshTokenEndpoint,
            long refreshTokenExpirationInSeconds,
            Consumer<String> connectionLostHandler) {
        this(credentialStore, tokenStore, httpClient, Clock.systemUTC(), tokenName, authorizationEndpoint,
                refreshTokenEndpoint, refreshTokenExpirationInSeconds, connectionLostHandler);
    }

    ConfigurableOAuth2AuthorizationService(
            OAuth2CredentialStore credentialStore,
            OAuth2TokenStore tokenStore,
            JsonHttpClient httpClient,
            Clock clock,
            String tokenName,
            String authorizationEndpoint,
            String refreshTokenEndpoint,
            long refreshTokenExpirationInSeconds,
            Consumer<String> connectionLostHandler) {
        this.credentialStore = credentialStore;
        this.tokenStore = tokenStore;
        this.httpClient = httpClient;
        this.clock = clock;
        this.tokenName = tokenName;
        this.authorizationEndpoint = authorizationEndpoint;
        this.refreshTokenEndpoint = refreshTokenEndpoint;
        this.refreshTokenExpirationInSeconds = refreshTokenExpirationInSeconds;
        this.connectionLostHandler = connectionLostHandler;
    }

    static boolean isAuthorizationLost(int statusCode, boolean passwordGrant) {
        if (statusCode == 403) {
            return true;
        }
        return statusCode == 400 && !passwordGrant;
    }

    static boolean isRefreshTokenRejected(HttpClientException e) {
        if (e.getStatusCode() == 403) {
            return true;
        }
        return e.getStatusCode() == 400
                && e.getResponseBody() != null
                && e.getResponseBody().contains("invalid_grant");
    }

    public String getAccessToken(String storeId) {
        Optional<OAuth2AccessToken> op = tokenStore.getToken(
                storeId, tokenName, ACCESS_TOKEN, OAuth2AccessToken.class);

        if (!op.isPresent() || op.get().isExpired()) {
            return refreshAccessToken(storeId, getRefreshToken(storeId), false);
        }

        return op.get().getTokenValue();
    }

    /**
     * Called after the API answered 401: the cached access token is treated as revoked and refreshed
     * regardless of its local expiry, through the regular refresh / authorize path.
     * <p>
     * At most one renewal per {@link #RENEWAL_COOLDOWN} runs per store and token name; the window is shared
     * by every instance of this class in the JVM. Inside the window the cached token is handed out instead.
     * The cached access token is only ever replaced, never emptied, so a concurrent caller never finds an
     * empty cache; only a locally expired cached token still makes {@link #getAccessToken} refresh on its own.
     * <p>
     * Returns null when no new token could be obtained.
     */
    public synchronized String renewAccessToken(String storeId) {
        Instant now = clock.instant();
        AtomicBoolean renewalWon = new AtomicBoolean();
        LAST_RENEWAL.compute(storeId + "|" + tokenName, (key, last) -> {
            if (last != null && Duration.between(last, now).compareTo(RENEWAL_COOLDOWN) < 0) {
                return last;
            }
            renewalWon.set(true);
            return now;
        });
        if (!renewalWon.get()) {
            // another call renewed the token a moment ago (or the account is genuinely broken): hand out
            // whatever the cache holds, read directly so that a loser can never reach the token endpoint
            return tokenStore.getToken(storeId, tokenName, ACCESS_TOKEN, OAuth2AccessToken.class)
                    .map(OAuth2AccessToken::getTokenValue)
                    .orElse(null);
        }
        log.warn("Access token for {} rejected by the API, renewing (store={})", tokenName, storeId);
        // the cached access token is deliberately kept until the refresh succeeds and overwrites it:
        // evicting it first would let a concurrent caller spend the same (single-use) refresh token
        return refreshAccessToken(storeId, getRefreshToken(storeId), true);
    }

    /**
     * Refreshes under the JVM-wide lock of this store and token name. Inside the lock the stored tokens are read
     * again: a caller that waited for the lock usually finds the pair another caller has just stored, and uses it
     * instead of spending a refresh token that is no longer valid.
     *
     * @param refreshTokenSeen   the refresh token the caller read before taking the lock (null when there was none)
     * @param accessTokenRejected true when the API rejected the stored access token (401): a stored access token is
     *                            then reused only if the refresh token was rotated meanwhile, since the unexpired
     *                            one may be exactly the token the API has just refused
     */
    private String refreshAccessToken(String storeId, String refreshTokenSeen, boolean accessTokenRejected) {
        synchronized (REFRESH_LOCKS.computeIfAbsent(storeId + "|" + tokenName, key -> new Object())) {
            String refreshToken = getRefreshToken(storeId);
            boolean rotatedMeanwhile = refreshToken != null && !refreshToken.equals(refreshTokenSeen);
            if (rotatedMeanwhile || !accessTokenRejected) {
                Optional<OAuth2AccessToken> access = tokenStore.getToken(
                        storeId, tokenName, ACCESS_TOKEN, OAuth2AccessToken.class);
                if (access.isPresent() && !access.get().isExpired()) {
                    return access.get().getTokenValue();
                }
            }
            if (refreshToken == null) {
                return authorize(storeId);
            } else {
                return authenticate(storeId, refreshToken);
            }
        }
    }

    private String authorize(String storeId) {
        OAuth2Secrets secrets = credentialStore.getSecrets(storeId, tokenName);
        try {
            return requestPasswordGrant(storeId, secrets);
        } catch (HttpClientException e) {
            if (isAuthorizationLost(e.getStatusCode(), secrets.getUsername() != null)) {
                connectionLostHandler.accept(storeId);
            }
            return null;
        }
    }

    private String authenticate(String storeId, String refreshToken) {
        return authenticate(storeId, refreshToken, false);
    }

    private String authenticate(String storeId, String refreshToken, boolean usingTokenRotatedElsewhere) {
        OAuth2Secrets secrets = credentialStore.getSecrets(storeId, tokenName);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);

        String authHeader = createBasicAuthHeader(secrets);

        try {
            OAuth2AuthorizationResponse authResponse = postFormEncoded(
                    refreshTokenEndpoint, params, authHeader);
            storeCredentials(storeId, authResponse);
            return authResponse.getAccessToken();
        } catch (HttpClientException e) {
            if (!isRefreshTokenRejected(e)) {
                return null;
            }
            if (!usingTokenRotatedElsewhere) {
                String stored = getRefreshToken(storeId);
                if (stored != null && !stored.equals(refreshToken)) {
                    // single-use refresh tokens (Allegro): another process (the lock covers only this JVM) spent
                    // this one first and stored the rotated pair meanwhile; the authorization is fine, so use that
                    // pair instead of reporting a loss. Narrows the cross-process race, does not close it: the other
                    // process may not have stored its pair yet when this read happens
                    log.info("Refresh token for {} was rotated by another process, using the stored one (store={})",
                            tokenName, storeId);
                    Optional<OAuth2AccessToken> access = tokenStore.getToken(
                            storeId, tokenName, ACCESS_TOKEN, OAuth2AccessToken.class);
                    if (access.isPresent() && !access.get().isExpired()) {
                        return access.get().getTokenValue();
                    }
                    return authenticate(storeId, stored, true);
                }
            }
            if (secrets.getUsername() != null) {
                // the refresh token was revoked together with the session, but a password grant can start a new one
                log.warn("Refresh token for {} rejected, re-authorizing with the password grant (store={})",
                        tokenName, storeId);
                tokenStore.deleteToken(storeId, tokenName, REFRESH_TOKEN);
                try {
                    return requestPasswordGrant(storeId, secrets);
                } catch (HttpClientException fallbackFailure) {
                    if (fallbackFailure.getStatusCode() < 500) {
                        // the refresh token was rejected and the password grant was refused too: the account
                        // is unusable; a 5xx on the token endpoint is an outage, not a revocation
                        connectionLostHandler.accept(storeId);
                    }
                    return null;
                }
            }
            connectionLostHandler.accept(storeId);
            return null;
        }
    }

    /** Password grant against the authorization endpoint; stores the tokens and returns the access token. */
    private String requestPasswordGrant(String storeId, OAuth2Secrets secrets) {
        Map<String, String> params = getAuthorizationRequestParams(secrets);
        String authHeader = createBasicAuthHeader(secrets);
        OAuth2AuthorizationResponse authResponse = postFormEncoded(authorizationEndpoint, params, authHeader);
        storeCredentials(storeId, authResponse);
        return authResponse.getAccessToken();
    }

    private OAuth2AuthorizationResponse postFormEncoded(String url, Map<String, String> params, String authHeader) {
        String formBody = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        return httpClient.sendAndParse(request, OAuth2AuthorizationResponse.class);
    }

    private String createBasicAuthHeader(OAuth2Secrets secrets) {
        String credentials = secrets.getClientId() + ":" + secrets.getClientSecret();
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encodedCredentials;
    }

    private String getRefreshToken(String storeId) {
        Optional<OAuth2RefreshToken> op = tokenStore.getToken(
                storeId, tokenName, REFRESH_TOKEN, OAuth2RefreshToken.class);
        if (op.isPresent() && !op.get().isExpired()) {
            return op.get().getTokenValue();
        }
        return null;
    }

    private void storeCredentials(String storeId, OAuth2AuthorizationResponse authResponse) {
        long now = clock.millis();
        long accessTokenExpiryTime = now + authResponse.getExpiresIn() * 1000;
        long refreshTokenExpiryTime = now + refreshTokenExpirationInSeconds * 1000;

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                authResponse.getAccessToken(),
                Instant.ofEpochMilli(now),
                Instant.ofEpochMilli(accessTokenExpiryTime)
        );

        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                authResponse.getRefreshToken(),
                Instant.ofEpochMilli(now),
                Instant.ofEpochMilli(refreshTokenExpiryTime)
        );

        tokenStore.storeToken(storeId, tokenName, ACCESS_TOKEN, accessToken);
        tokenStore.storeToken(storeId, tokenName, REFRESH_TOKEN, refreshToken);
    }

    private Map<String, String> getAuthorizationRequestParams(OAuth2Secrets secrets) {
        Map<String, String> params = new LinkedHashMap<>();

        if (secrets.getUsername() != null) {
            params.put("grant_type", "password");
            params.put("scope", "api");
            params.put("username", secrets.getUsername());
            params.put("password", secrets.getPassword());
        }

        return params;
    }
}
