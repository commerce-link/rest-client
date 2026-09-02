package pl.commercelink.rest.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestApiWithRetryTest {

    /** Answers 401 until the bearer token equals the accepted one, then returns "ok". */
    private static class FakeRestApi extends RestApi {

        private final String acceptedToken;
        private String currentToken;
        final List<String> tokensSet = new ArrayList<>();
        int calls;

        FakeRestApi(String acceptedToken) {
            super("https://api.example");
            this.acceptedToken = acceptedToken;
        }

        @Override
        void setBearerToken(String bearerToken) {
            tokensSet.add(bearerToken);
            currentToken = bearerToken;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T fetch(String endpoint, Map<String, String> params, Class<T> responseType) {
            calls++;
            if (!acceptedToken.equals(currentToken)) {
                throw new HttpClientException(401, "{\"code\":\"access_denied\",\"details\":\"Access token has been revoked\"}");
            }
            return (T) "ok";
        }
    }

    @Test
    void unauthorizedResponseRenewsTheTokenAndRetriesOnce() {
        // given: the cached token is revoked, the renewer obtains a fresh one
        FakeRestApi api = new FakeRestApi("at-new");
        api.setBearerToken("at-revoked");
        AtomicInteger renewals = new AtomicInteger();
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-revoked", () -> {
            renewals.incrementAndGet();
            return "at-new";
        });

        // when
        String result = retry.fetchWithAuthRetry("/account", Map.of(), String.class);

        // then
        assertEquals("ok", result);
        assertEquals(1, renewals.get());
        assertEquals(2, api.calls);
        assertEquals(List.of("at-revoked", "at-new"), api.tokensSet);
    }

    @Test
    void failedRenewalRethrowsTheOriginalUnauthorizedResponse() {
        // given
        FakeRestApi api = new FakeRestApi("at-new");
        api.setBearerToken("at-revoked");
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-revoked", () -> null);

        // when / then
        HttpClientException e = assertThrows(HttpClientException.class,
                () -> retry.fetchWithAuthRetry("/account", Map.of(), String.class));
        assertEquals(401, e.getStatusCode());
        assertEquals(1, api.calls);
        assertEquals(List.of("at-revoked"), api.tokensSet);
    }

    @Test
    void secondUnauthorizedResponseAfterRenewalPropagates() {
        // given: the renewer hands out a token the API still rejects
        FakeRestApi api = new FakeRestApi("at-accepted");
        api.setBearerToken("at-revoked");
        AtomicInteger renewals = new AtomicInteger();
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-revoked", () -> {
            renewals.incrementAndGet();
            return "at-still-bad";
        });

        // when / then
        HttpClientException e = assertThrows(HttpClientException.class,
                () -> retry.fetchWithAuthRetry("/account", Map.of(), String.class));
        assertEquals(401, e.getStatusCode());
        assertEquals(1, renewals.get());
        assertEquals(2, api.calls);
    }

    @Test
    void otherErrorsDoNotTouchTheToken() {
        // given
        FakeRestApi api = new FakeRestApi("at-ok") {
            @Override
            public <T> T fetch(String endpoint, Map<String, String> params, Class<T> responseType) {
                calls++;
                throw new HttpClientException(500, "boom");
            }
        };
        AtomicInteger renewals = new AtomicInteger();
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-ok", () -> {
            renewals.incrementAndGet();
            return "at-ok";
        });

        // when / then
        HttpClientException e = assertThrows(HttpClientException.class,
                () -> retry.fetchWithAuthRetry("/account", Map.of(), String.class));
        assertEquals(500, e.getStatusCode());
        assertEquals(0, renewals.get());
        assertEquals(1, api.calls);
    }

    @Test
    void legacyConstructorKeepsUsingTheSupplierAfterUnauthorized() {
        // given: adapters built with the two-argument constructor behave as before
        FakeRestApi api = new FakeRestApi("at-new");
        api.setBearerToken("at-revoked");
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-new");

        // when
        String result = retry.fetchWithAuthRetry("/account", Map.of(), String.class);

        // then
        assertEquals("ok", result);
        assertEquals(List.of("at-revoked", "at-new"), api.tokensSet);
    }
}
