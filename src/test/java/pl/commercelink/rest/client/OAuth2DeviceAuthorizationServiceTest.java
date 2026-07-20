package pl.commercelink.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuth2DeviceAuthorizationServiceTest {

    private static final String EXPECTED_BASIC_AUTH =
            "Basic " + Base64.getEncoder().encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));

    @Test
    void startDeviceAuthorizationSendsBasicAuthFormRequestAndParsesResponse() throws Exception {
        // given
        OAuth2DeviceAuthorization parsed = new ObjectMapper().readValue("""
                {"device_code":"dev-1","user_code":"ABC123",
                 "verification_uri":"https://allegro.pl/skojarz-aplikacje",
                 "verification_uri_complete":"https://allegro.pl/skojarz-aplikacje?code=ABC123",
                 "expires_in":3600,"interval":5,"unknown_field":"x"}
                """, OAuth2DeviceAuthorization.class);
        CapturingJsonHttpClient httpClient = new CapturingJsonHttpClient(parsed);
        OAuth2DeviceAuthorizationService service = new OAuth2DeviceAuthorizationService(httpClient);

        // when
        OAuth2DeviceAuthorization result = service.startDeviceAuthorization(
                "https://allegro.pl/auth/oauth/device", "client-id", "client-secret");

        // then
        assertEquals("dev-1", result.getDeviceCode());
        assertEquals("ABC123", result.getUserCode());
        assertEquals("https://allegro.pl/skojarz-aplikacje?code=ABC123", result.getVerificationUriComplete());
        assertEquals(5L, result.getInterval());
        assertEquals("https://allegro.pl/auth/oauth/device", httpClient.request.uri().toString());
        assertEquals(EXPECTED_BASIC_AUTH, httpClient.request.headers().firstValue("Authorization").orElse(null));
        assertEquals("application/x-www-form-urlencoded",
                httpClient.request.headers().firstValue("Content-Type").orElse(null));
        assertEquals("client_id=client-id", readBody(httpClient.request));
    }

    @Test
    void pollDeviceTokenSendsDeviceCodeGrantAndReturnsAuthorizedTokens() throws Exception {
        // given
        OAuth2AuthorizationResponse response = new ObjectMapper().readValue(
                "{\"access_token\":\"acc-1\",\"refresh_token\":\"ref-1\",\"expires_in\":43199,\"scope\":\"x\",\"jti\":\"y\"}",
                OAuth2AuthorizationResponse.class);
        CapturingJsonHttpClient httpClient = new CapturingJsonHttpClient(response);
        OAuth2DeviceAuthorizationService service = new OAuth2DeviceAuthorizationService(httpClient);

        // when
        OAuth2DeviceTokenResult result = service.pollDeviceToken(
                "https://allegro.pl/auth/oauth/token", "client-id", "client-secret", "dev-1");

        // then
        assertEquals(OAuth2DeviceTokenResult.Status.AUTHORIZED, result.status());
        assertEquals("acc-1", result.accessToken());
        assertEquals("ref-1", result.refreshToken());
        assertEquals(43199L, result.accessTokenExpiresInSeconds());
        assertEquals("https://allegro.pl/auth/oauth/token", httpClient.request.uri().toString());
        assertEquals(EXPECTED_BASIC_AUTH, httpClient.request.headers().firstValue("Authorization").orElse(null));
        String body = readBody(httpClient.request);
        assertTrue(body.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"));
        assertTrue(body.contains("device_code=dev-1"));
    }

    @Test
    void pollDeviceTokenReturnsPendingWhileUserHasNotConfirmed() {
        // given
        ThrowingJsonHttpClient httpClient = new ThrowingJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"authorization_pending\"}"));
        OAuth2DeviceAuthorizationService service = new OAuth2DeviceAuthorizationService(httpClient);

        // when
        OAuth2DeviceTokenResult result = service.pollDeviceToken(
                "https://token", "client-id", "client-secret", "dev-1");

        // then
        assertEquals(OAuth2DeviceTokenResult.Status.PENDING, result.status());
        assertNull(result.error());
    }

    @Test
    void pollDeviceTokenReturnsSlowDownWhenThrottled() {
        // given
        ThrowingJsonHttpClient httpClient = new ThrowingJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"slow_down\"}"));
        OAuth2DeviceAuthorizationService service = new OAuth2DeviceAuthorizationService(httpClient);

        // when / then
        assertEquals(OAuth2DeviceTokenResult.Status.SLOW_DOWN,
                service.pollDeviceToken("https://token", "client-id", "client-secret", "dev-1").status());
    }

    @Test
    void pollDeviceTokenReturnsFailedWithErrorWhenUserDeniesOrCodeExpires() {
        // given
        ThrowingJsonHttpClient httpClient = new ThrowingJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"access_denied\"}"));
        OAuth2DeviceAuthorizationService service = new OAuth2DeviceAuthorizationService(httpClient);

        // when
        OAuth2DeviceTokenResult result = service.pollDeviceToken(
                "https://token", "client-id", "client-secret", "dev-1");

        // then
        assertEquals(OAuth2DeviceTokenResult.Status.FAILED, result.status());
        assertEquals("access_denied", result.error());
    }

    @Test
    void pollDeviceTokenReturnsFailedOnMalformedErrorBody() {
        // given
        ThrowingJsonHttpClient httpClient = new ThrowingJsonHttpClient(
                new HttpClientException(400, "not-json"));
        OAuth2DeviceAuthorizationService service = new OAuth2DeviceAuthorizationService(httpClient);

        // when
        OAuth2DeviceTokenResult result = service.pollDeviceToken(
                "https://token", "client-id", "client-secret", "dev-1");

        // then
        assertEquals(OAuth2DeviceTokenResult.Status.FAILED, result.status());
        assertEquals("not-json", result.error());
    }

    @Test
    void pollDeviceTokenRethrowsServerErrors() {
        // given
        ThrowingJsonHttpClient httpClient = new ThrowingJsonHttpClient(
                new HttpClientException(502, "bad gateway"));
        OAuth2DeviceAuthorizationService service = new OAuth2DeviceAuthorizationService(httpClient);

        // when / then
        assertThrows(HttpClientException.class, () ->
                service.pollDeviceToken("https://token", "client-id", "client-secret", "dev-1"));
    }

    private static String readBody(HttpRequest request) throws Exception {
        CompletableFuture<String> body = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                buffer.append(StandardCharsets.UTF_8.decode(item));
            }

            @Override
            public void onError(Throwable throwable) {
                body.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                body.complete(buffer.toString());
            }
        });
        return body.get();
    }

    private static class CapturingJsonHttpClient extends JsonHttpClient {

        private final Object response;
        private HttpRequest request;

        CapturingJsonHttpClient(Object response) {
            this.response = response;
        }

        @Override
        @SuppressWarnings("unchecked")
        <T> T sendAndParse(HttpRequest request, Class<T> responseType) {
            this.request = request;
            return (T) response;
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
