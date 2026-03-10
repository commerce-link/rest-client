package pl.commercelink.rest.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ConfigurableOAuth2AuthorizationService {

    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";

    private final OAuth2CredentialStore credentialStore;
    private final OAuth2TokenStore tokenStore;
    private final JsonHttpClient httpClient;
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
        this.credentialStore = credentialStore;
        this.tokenStore = tokenStore;
        this.httpClient = new JsonHttpClient();
        this.tokenName = tokenName;
        this.authorizationEndpoint = authorizationEndpoint;
        this.refreshTokenEndpoint = refreshTokenEndpoint;
        this.refreshTokenExpirationInSeconds = refreshTokenExpirationInSeconds;
        this.connectionLostHandler = connectionLostHandler;
    }

    public String getAccessToken(String storeId) {
        Optional<OAuth2AccessToken> op = tokenStore.getToken(
                storeId, tokenName, ACCESS_TOKEN, OAuth2AccessToken.class);

        if (!op.isPresent() || op.get().isExpired()) {
            return refreshAccessToken(storeId);
        }

        return op.get().getTokenValue();
    }

    private String refreshAccessToken(String storeId) {
        String refreshToken = getRefreshToken(storeId);
        if (refreshToken == null) {
            return authorize(storeId);
        } else {
            return authenticate(storeId, refreshToken);
        }
    }

    private synchronized String authorize(String storeId) {
        OAuth2Secrets secrets = credentialStore.getSecrets(storeId, tokenName);
        Map<String, String> params = getAuthorizationRequestParams(secrets);
        String authHeader = createBasicAuthHeader(secrets);

        try {
            OAuth2AuthorizationResponse authResponse = postFormEncoded(
                    authorizationEndpoint, params, authHeader);
            storeCredentials(storeId, authResponse);
            return authResponse.getAccessToken();
        } catch (HttpClientException e) {
            if (e.getStatusCode() == 403) {
                connectionLostHandler.accept(storeId);
            }
            return null;
        }
    }

    private synchronized String authenticate(String storeId, String refreshToken) {
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
            if (e.getStatusCode() == 403) {
                connectionLostHandler.accept(storeId);
            }
            return null;
        }
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
        long now = System.currentTimeMillis();
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
