package pl.commercelink.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurableOAuth2AuthorizationServiceTest {

    @Test
    void authorizeAuthorizationLostForBadRequestAndForbidden() {
        // given / when / then
        assertTrue(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(400, false));
        assertFalse(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(400, true));
        assertTrue(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(403, false));
        assertTrue(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(403, true));
        assertFalse(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(401, false));
        assertFalse(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(500, false));
    }

    @Test
    void refreshTokenRejectedForForbiddenAndInvalidGrantOnly() {
        // given / when / then
        assertTrue(ConfigurableOAuth2AuthorizationService.isRefreshTokenRejected(
                new HttpClientException(403, "{\"error\":\"forbidden\"}")));
        assertTrue(ConfigurableOAuth2AuthorizationService.isRefreshTokenRejected(
                new HttpClientException(400, "{\"error\":\"invalid_grant\"}")));
        assertFalse(ConfigurableOAuth2AuthorizationService.isRefreshTokenRejected(
                new HttpClientException(400, "{\"error\":\"invalid_request\"}")));
        assertFalse(ConfigurableOAuth2AuthorizationService.isRefreshTokenRejected(
                new HttpClientException(400, null)));
        assertFalse(ConfigurableOAuth2AuthorizationService.isRefreshTokenRejected(
                new HttpClientException(401, "{\"error\":\"invalid_grant\"}")));
    }

    @Test
    void refreshWith400InvalidGrantMarksConnectionLost() {
        // given
        String storeId = "store-1";
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2RefreshToken("refresh-token-value", Instant.now(), Instant.now().plusSeconds(3600)));
        ThrowingJsonHttpClient throwingHttpClient = new ThrowingJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_grant\"}"));
        List<String> lostConnections = new ArrayList<>();

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                throwingHttpClient,
                "allegro",
                "https://allegro.pl/auth/oauth/token",
                "https://allegro.pl/auth/oauth/token",
                3600L,
                lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertNull(accessToken);
        assertEquals(List.of(storeId), lostConnections);
    }

    @Test
    void refreshWithOther400DoesNotMarkConnectionLost() {
        // given
        String storeId = "store-1";
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2RefreshToken("refresh-token-value", Instant.now(), Instant.now().plusSeconds(3600)));
        ThrowingJsonHttpClient throwingHttpClient = new ThrowingJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_request\"}"));
        List<String> lostConnections = new ArrayList<>();

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                throwingHttpClient,
                "allegro",
                "https://allegro.pl/auth/oauth/token",
                "https://allegro.pl/auth/oauth/token",
                3600L,
                lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertNull(accessToken);
        assertTrue(lostConnections.isEmpty());
    }

    @Test
    void refreshWith403MarksConnectionLost() {
        // given
        String storeId = "store-1";
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2RefreshToken("refresh-token-value", Instant.now(), Instant.now().plusSeconds(3600)));
        ThrowingJsonHttpClient throwingHttpClient = new ThrowingJsonHttpClient(
                new HttpClientException(403, "{\"error\":\"forbidden\"}"));
        List<String> lostConnections = new ArrayList<>();

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                throwingHttpClient,
                "allegro",
                "https://allegro.pl/auth/oauth/token",
                "https://allegro.pl/auth/oauth/token",
                3600L,
                lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertNull(accessToken);
        assertEquals(List.of(storeId), lostConnections);
    }

    @Test
    void authorizeWith400WithoutUsernameMarksConnectionLost() {
        // given
        String storeId = "store-1";
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(null);
        ThrowingJsonHttpClient throwingHttpClient = new ThrowingJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_request\"}"));
        List<String> lostConnections = new ArrayList<>();

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                throwingHttpClient,
                "allegro",
                "https://allegro.pl/auth/oauth/token",
                "https://allegro.pl/auth/oauth/token",
                3600L,
                lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertNull(accessToken);
        assertEquals(List.of(storeId), lostConnections);
    }

    @Test
    void authorizeWith400WithUsernameDoesNotMarkConnectionLost() {
        // given
        String storeId = "store-1";
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret", "username", "password"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(null);
        ThrowingJsonHttpClient throwingHttpClient = new ThrowingJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_request\"}"));
        List<String> lostConnections = new ArrayList<>();

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                throwingHttpClient,
                "furgonetka",
                "https://furgonetka.pl/oauth/token",
                "https://furgonetka.pl/oauth/token",
                3600L,
                lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertNull(accessToken);
        assertTrue(lostConnections.isEmpty());
    }

    @Test
    void authorizeWith403WithUsernameMarksConnectionLost() {
        // given
        String storeId = "store-1";
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret", "username", "password"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(null);
        ThrowingJsonHttpClient throwingHttpClient = new ThrowingJsonHttpClient(
                new HttpClientException(403, "{\"error\":\"forbidden\"}"));
        List<String> lostConnections = new ArrayList<>();

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                throwingHttpClient,
                "furgonetka",
                "https://furgonetka.pl/oauth/token",
                "https://furgonetka.pl/oauth/token",
                3600L,
                lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertNull(accessToken);
        assertEquals(List.of(storeId), lostConnections);
    }

    @Test
    void successfulRefreshPersistsRotatedTokens() throws Exception {
        // given
        String storeId = "store-1";
        long refreshTokenExpirationInSeconds = 3600L;
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2RefreshToken("refresh-token-value", Instant.now(), Instant.now().plusSeconds(3600)));
        OAuth2AuthorizationResponse response = new ObjectMapper().readValue(
                "{\"access_token\":\"at-new\",\"refresh_token\":\"rt-new\",\"expires_in\":43199,"
                        + "\"token_type\":\"bearer\"}",
                OAuth2AuthorizationResponse.class);
        RespondingJsonHttpClient respondingHttpClient = new RespondingJsonHttpClient(response);

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                respondingHttpClient,
                "allegro",
                "https://allegro.pl/auth/oauth/token",
                "https://allegro.pl/auth/oauth/token",
                refreshTokenExpirationInSeconds,
                storeIdArg -> { });

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertEquals("at-new", accessToken);
        assertNotNull(tokenStore.storedAccessToken);
        assertEquals("at-new", ((OAuth2AccessToken) tokenStore.storedAccessToken).getTokenValue());
        assertNotNull(tokenStore.storedRefreshToken);
        OAuth2RefreshToken storedRefreshToken = (OAuth2RefreshToken) tokenStore.storedRefreshToken;
        assertEquals("rt-new", storedRefreshToken.getTokenValue());
        assertEquals(refreshTokenExpirationInSeconds * 1000,
                storedRefreshToken.getExpiresAt().toEpochMilli() - storedRefreshToken.getIssuedAt().toEpochMilli());
    }

    @Test
    void renewAccessTokenEvictsCachedAccessTokenAndRefreshesEvenWhenNotExpired() throws Exception {
        // given: cached access token still valid for 30 days according to the local clock
        String storeId = "store-1";
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-revoked", Instant.now(), Instant.now().plusSeconds(2_592_000)),
                new OAuth2RefreshToken("rt-old", Instant.now(), Instant.now().plusSeconds(3600)));
        SequenceJsonHttpClient http = new SequenceJsonHttpClient(tokenResponse("at-new", "rt-new"));
        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret")),
                tokenStore, http, Clock.systemUTC(),
                "furgonetka", "https://api.furgonetka.pl/oauth/token", "https://api.furgonetka.pl/oauth/token",
                3600L, storeIdArg -> { });

        // when
        String renewed = service.renewAccessToken(storeId);

        // then
        assertEquals("at-new", renewed);
        assertEquals(List.of(ConfigurableOAuth2AuthorizationService.ACCESS_TOKEN), tokenStore.deletedTokenTypes);
        assertEquals("at-new", ((OAuth2AccessToken) tokenStore.storedAccessToken).getTokenValue());
        assertEquals("at-new", service.getAccessToken(storeId));
        assertEquals(1, http.calls);
    }

    private static OAuth2AuthorizationResponse tokenResponse(String accessToken, String refreshToken) throws Exception {
        return new ObjectMapper().readValue(
                "{\"access_token\":\"" + accessToken + "\",\"refresh_token\":\"" + refreshToken
                        + "\",\"expires_in\":2592000,\"token_type\":\"bearer\"}",
                OAuth2AuthorizationResponse.class);
    }

    private static class FakeOAuth2CredentialStore implements OAuth2CredentialStore {

        private final OAuth2Secrets secrets;

        FakeOAuth2CredentialStore(OAuth2Secrets secrets) {
            this.secrets = secrets;
        }

        @Override
        public void createOrUpdateSecrets(String key, String tokenName, OAuth2Secrets secrets) {
        }

        @Override
        public OAuth2Secrets getSecrets(String key, String tokenName) {
            return secrets;
        }
    }

    private static class FakeOAuth2TokenStore implements OAuth2TokenStore {

        private OAuth2AccessToken accessToken;
        private OAuth2RefreshToken refreshToken;
        private Object storedAccessToken;
        private Object storedRefreshToken;
        private final List<String> deletedTokenTypes = new ArrayList<>();

        FakeOAuth2TokenStore(OAuth2RefreshToken refreshToken) {
            this(null, refreshToken);
        }

        FakeOAuth2TokenStore(OAuth2AccessToken accessToken, OAuth2RefreshToken refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> getToken(String key, String tokenName, String tokenType, Class<T> clazz) {
            if (ConfigurableOAuth2AuthorizationService.ACCESS_TOKEN.equals(tokenType) && accessToken != null) {
                return Optional.of((T) accessToken);
            }
            if (ConfigurableOAuth2AuthorizationService.REFRESH_TOKEN.equals(tokenType) && refreshToken != null) {
                return Optional.of((T) refreshToken);
            }
            return Optional.empty();
        }

        @Override
        public void storeToken(String key, String tokenName, String tokenType, Object token) {
            if (ConfigurableOAuth2AuthorizationService.ACCESS_TOKEN.equals(tokenType)) {
                storedAccessToken = token;
                accessToken = (OAuth2AccessToken) token;
            } else if (ConfigurableOAuth2AuthorizationService.REFRESH_TOKEN.equals(tokenType)) {
                storedRefreshToken = token;
                refreshToken = (OAuth2RefreshToken) token;
            }
        }

        @Override
        public void deleteToken(String key, String tokenName, String tokenType) {
            deletedTokenTypes.add(tokenType);
            if (ConfigurableOAuth2AuthorizationService.ACCESS_TOKEN.equals(tokenType)) {
                accessToken = null;
            } else if (ConfigurableOAuth2AuthorizationService.REFRESH_TOKEN.equals(tokenType)) {
                refreshToken = null;
            }
        }
    }

    /** Each call to sendAndParse consumes the next element: an HttpClientException is thrown, anything else returned. */
    private static class SequenceJsonHttpClient extends JsonHttpClient {

        private final java.util.Deque<Object> outcomes;
        private int calls;

        SequenceJsonHttpClient(Object... outcomes) {
            this.outcomes = new java.util.ArrayDeque<>(List.of(outcomes));
        }

        @Override
        @SuppressWarnings("unchecked")
        <T> T sendAndParse(HttpRequest request, Class<T> responseType) {
            calls++;
            Object next = outcomes.removeFirst();
            if (next instanceof HttpClientException e) {
                throw e;
            }
            return (T) next;
        }
    }

    private static class ThrowingJsonHttpClient extends JsonHttpClient {

        private final HttpClientException exception;

        ThrowingJsonHttpClient(HttpClientException exception) {
            this.exception = exception;
        }

        @Override
        <T> T sendAndParse(HttpRequest request, Class<T> responseType) {
            throw exception;
        }
    }

    private static class RespondingJsonHttpClient extends JsonHttpClient {

        private final OAuth2AuthorizationResponse response;

        RespondingJsonHttpClient(OAuth2AuthorizationResponse response) {
            this.response = response;
        }

        @Override
        @SuppressWarnings("unchecked")
        <T> T sendAndParse(HttpRequest request, Class<T> responseType) {
            return (T) response;
        }
    }
}
