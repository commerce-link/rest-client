package pl.commercelink.rest.client;

import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurableOAuth2AuthorizationServiceTest {

    @Test
    void authorizationLostForBadRequestAndForbidden() {
        // given / when / then
        assertTrue(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(400));
        assertTrue(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(403));
        assertFalse(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(401));
        assertFalse(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(500));
    }

    @Test
    void invalidGrantOnRefreshInvokesConnectionLostHandler() {
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

        private final OAuth2RefreshToken refreshToken;

        FakeOAuth2TokenStore(OAuth2RefreshToken refreshToken) {
            this.refreshToken = refreshToken;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> getToken(String key, String tokenName, String tokenType, Class<T> clazz) {
            if (ConfigurableOAuth2AuthorizationService.REFRESH_TOKEN.equals(tokenType)) {
                return Optional.of((T) refreshToken);
            }
            return Optional.empty();
        }

        @Override
        public void storeToken(String key, String tokenName, String tokenType, Object token) {
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
}
