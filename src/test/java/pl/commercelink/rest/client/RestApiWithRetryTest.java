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

        String acceptedToken;
        private String currentToken;
        final List<String> tokensSet = new ArrayList<>();
        /** Per-request headers forwarded by the 4-arg overloads, one entry per API call. */
        final List<Map<String, String>> headersSeen = new ArrayList<>();
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

        @Override
        public <T> T fetch(String endpoint, Map<String, String> params, Map<String, String> headers,
                           Class<T> responseType) {
            headersSeen.add(headers);
            return fetch(endpoint, params, responseType);
        }

        @Override
        public <T> T post(String endpoint, Object body, Map<String, String> headers, Class<T> responseType) {
            headersSeen.add(headers);
            return fetch(endpoint, Map.of(), responseType);
        }
    }

    @Test
    void firstCallWithoutTokenUsesTheCachedTokenBeforeRenewing() {
        // given: the client was built without a bearer token, the cached token is still valid
        FakeRestApi api = new FakeRestApi("at-cached");
        AtomicInteger renewals = new AtomicInteger();
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-cached", () -> {
            renewals.incrementAndGet();
            return "at-renewed";
        });

        // when
        String result = retry.fetchWithAuthRetry("/account", Map.of(), String.class);

        // then: the bootstrap 401 must not cost a token renewal
        assertEquals("ok", result);
        assertEquals(0, renewals.get());
        assertEquals(2, api.calls);
        assertEquals(List.of("at-cached"), api.tokensSet);
    }

    @Test
    void cachedTokenRejectedTwiceTriggersRenewal() {
        // given: the cached token is revoked at the provider, the renewer obtains a fresh one
        FakeRestApi api = new FakeRestApi("at-new");
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
        assertEquals(3, api.calls);
        assertEquals(List.of("at-revoked", "at-new"), api.tokensSet);
    }

    @Test
    void unauthorizedResponseRenewsTheTokenWhenTheCachedTokenIsAlreadySet() {
        // given: the first call bootstrapped the bearer token from the cache
        FakeRestApi api = new FakeRestApi("at-1");
        AtomicInteger renewals = new AtomicInteger();
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-1", () -> {
            renewals.incrementAndGet();
            return "at-2";
        });
        assertEquals("ok", retry.fetchWithAuthRetry("/account", Map.of(), String.class));
        assertEquals(0, renewals.get());

        // when: the provider revokes that token and the cache still hands out the rejected one
        api.acceptedToken = "at-2";
        String result = retry.fetchWithAuthRetry("/account", Map.of(), String.class);

        // then: no pointless retry with the token that was just rejected
        assertEquals("ok", result);
        assertEquals(1, renewals.get());
        assertEquals(4, api.calls);
        assertEquals(List.of("at-1", "at-2"), api.tokensSet);
    }

    @Test
    void failedRenewalRethrowsTheUnauthorizedResponse() {
        // given
        FakeRestApi api = new FakeRestApi("at-new");
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-revoked", () -> null);

        // when / then
        HttpClientException e = assertThrows(HttpClientException.class,
                () -> retry.fetchWithAuthRetry("/account", Map.of(), String.class));
        assertEquals(401, e.getStatusCode());
        assertEquals(2, api.calls);
        assertEquals(List.of("at-revoked"), api.tokensSet);
    }

    @Test
    void renewerFailureCarriesTheUnauthorizedResponseAsSuppressed() {
        // given: the renewer cannot reach the token endpoint
        FakeRestApi api = new FakeRestApi("at-new");
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-revoked", () -> {
            throw new IllegalStateException("token endpoint down");
        });

        // when / then
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> retry.fetchWithAuthRetry("/account", Map.of(), String.class));
        assertEquals(1, e.getSuppressed().length);
        assertEquals(401, ((HttpClientException) e.getSuppressed()[0]).getStatusCode());
    }

    @Test
    void secondUnauthorizedResponseAfterRenewalPropagates() {
        // given: the renewer hands out a token the API still rejects
        FakeRestApi api = new FakeRestApi("at-accepted");
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
        assertEquals(3, api.calls);
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
    void serverErrorOnTheCachedTokenRetryPropagatesWithoutRenewing() {
        // given: the bootstrap 401 is followed by an unrelated server error
        FakeRestApi api = new FakeRestApi("at-cached") {
            @Override
            public <T> T fetch(String endpoint, Map<String, String> params, Class<T> responseType) {
                calls++;
                if (calls == 1) {
                    throw new HttpClientException(401, "{\"code\":\"access_denied\"}");
                }
                throw new HttpClientException(503, "maintenance");
            }
        };
        AtomicInteger renewals = new AtomicInteger();
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-cached", () -> {
            renewals.incrementAndGet();
            return "at-new";
        });

        // when / then
        HttpClientException e = assertThrows(HttpClientException.class,
                () -> retry.fetchWithAuthRetry("/account", Map.of(), String.class));
        assertEquals(503, e.getStatusCode());
        assertEquals(0, renewals.get());
        assertEquals(2, api.calls);
    }

    @Test
    void nullCachedTokenRethrowsWithoutRenewing() {
        // given: the supplier itself could not obtain a token, so the renewer would repeat the same failure
        FakeRestApi api = new FakeRestApi("at-new");
        AtomicInteger renewals = new AtomicInteger();
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> null, () -> {
            renewals.incrementAndGet();
            return "at-new";
        });

        // when / then
        HttpClientException e = assertThrows(HttpClientException.class,
                () -> retry.fetchWithAuthRetry("/account", Map.of(), String.class));
        assertEquals(401, e.getStatusCode());
        assertEquals(0, renewals.get());
        assertEquals(1, api.calls);
        assertEquals(List.of(), api.tokensSet);
    }

    @Test
    void renewerReturningTheRejectedTokenRethrowsWithoutAThirdAttempt() {
        // given: a renewal cooldown loser hands back the token the API has just rejected
        FakeRestApi api = new FakeRestApi("at-new");
        AtomicInteger renewals = new AtomicInteger();
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-revoked", () -> {
            renewals.incrementAndGet();
            return "at-revoked";
        });

        // when / then
        HttpClientException e = assertThrows(HttpClientException.class,
                () -> retry.fetchWithAuthRetry("/account", Map.of(), String.class));
        assertEquals(401, e.getStatusCode());
        assertEquals(1, renewals.get());
        assertEquals(2, api.calls);
    }

    @Test
    void legacyConstructorKeepsUsingTheSupplierAfterUnauthorized() {
        // given: adapters built with the two-argument constructor behave as before
        FakeRestApi api = new FakeRestApi("at-new");
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-new");

        // when
        String result = retry.fetchWithAuthRetry("/account", Map.of(), String.class);

        // then
        assertEquals("ok", result);
        assertEquals(2, api.calls);
        assertEquals(List.of("at-new"), api.tokensSet);
    }

    /*
     * The 4-arg fetch/post overloads: if either silently dropped the header map instead of forwarding it,
     * every caller relying on a per-request header override (e.g. Allegro's customer-returns beta media type)
     * would start getting HTTP 406 while every other module's tests stayed green.
     */

    private static final Map<String, String> BETA = Map.of("Accept", "application/vnd.allegro.beta.v1+json");

    @Test
    void fetchWithAuthRetryPassesPerRequestHeadersThrough() {
        // given
        FakeRestApi api = new FakeRestApi("at-ok");
        api.setBearerToken("at-ok");
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-ok");

        // when
        String result = retry.fetchWithAuthRetry("/x", Map.of(), BETA, String.class);

        // then
        assertEquals("ok", result);
        assertEquals(List.of(BETA), api.headersSeen);
    }

    @Test
    void postWithAuthRetryPassesPerRequestHeadersThrough() {
        // given
        FakeRestApi api = new FakeRestApi("at-ok");
        api.setBearerToken("at-ok");
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-ok");

        // when
        String result = retry.postWithAuthRetry("/x", new Object(), BETA, String.class);

        // then
        assertEquals("ok", result);
        assertEquals(List.of(BETA), api.headersSeen);
    }

    @Test
    void fetchWithAuthRetryRenewsTheTokenAndKeepsTheHeadersOnRetry() {
        // given: the cached token is revoked, the renewer obtains a fresh one
        FakeRestApi api = new FakeRestApi("at-new");
        AtomicInteger renewals = new AtomicInteger();
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-revoked", () -> {
            renewals.incrementAndGet();
            return "at-new";
        });

        // when
        String result = retry.fetchWithAuthRetry("/x", Map.of(), BETA, String.class);

        // then: every attempt (initial, cached-token retry, renewed-token retry) forwards the same headers
        assertEquals("ok", result);
        assertEquals(1, renewals.get());
        assertEquals(List.of(BETA, BETA, BETA), api.headersSeen);
        assertEquals(List.of("at-revoked", "at-new"), api.tokensSet);
    }

    @Test
    void postWithAuthRetryRenewsTheTokenAndKeepsTheHeadersOnRetry() {
        // given
        FakeRestApi api = new FakeRestApi("at-new");
        AtomicInteger renewals = new AtomicInteger();
        RestApiWithRetry retry = new RestApiWithRetry(api, () -> "at-revoked", () -> {
            renewals.incrementAndGet();
            return "at-new";
        });

        // when
        String result = retry.postWithAuthRetry("/x", new Object(), BETA, String.class);

        // then
        assertEquals("ok", result);
        assertEquals(1, renewals.get());
        assertEquals(List.of(BETA, BETA, BETA), api.headersSeen);
        assertEquals(List.of("at-revoked", "at-new"), api.tokensSet);
    }
}
