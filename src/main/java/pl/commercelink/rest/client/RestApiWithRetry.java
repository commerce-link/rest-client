package pl.commercelink.rest.client;

import java.util.Map;
import java.util.function.Supplier;

public class RestApiWithRetry {

    private final RestApi restApi;

    /** Current (possibly cached) token: tried after a 401 before paying for a renewal. */
    private final Supplier<String> accessTokenSupplier;

    private final Supplier<String> accessTokenRenewer;

    /** Last token handed to {@link RestApi#setBearerToken}; null until the first 401. */
    private String bearerToken;

    /** Legacy wiring: the same supplier answers both the initial token and the retry after 401. */
    public RestApiWithRetry(RestApi restApi, Supplier<String> accessTokenSupplier) {
        this(restApi, accessTokenSupplier, accessTokenSupplier);
    }

    /**
     * @param accessTokenSupplier current (possibly cached) access token
     * @param accessTokenRenewer  called after the API answered 401: must treat the cached token as revoked and
     *                            return a fresh one, or null when no token could be obtained
     */
    public RestApiWithRetry(RestApi restApi, Supplier<String> accessTokenSupplier, Supplier<String> accessTokenRenewer) {
        this.restApi = restApi;
        this.accessTokenSupplier = accessTokenSupplier;
        this.accessTokenRenewer = accessTokenRenewer;
    }

    public <T> T fetchWithAuthRetry(String endpoint, Map<String, String> params, Class<T> responseType) {
        return executeWithAuthRetry(() -> restApi.fetch(endpoint, params, responseType));
    }

    public <T> T postWithAuthRetry(String endpoint, Object body, Class<T> responseType) {
        return executeWithAuthRetry(() -> restApi.post(endpoint, body, responseType));
    }

    public <T> T putWithAuthRetry(String endpoint, Object body, Class<T> responseType) {
        return executeWithAuthRetry(() -> restApi.put(endpoint, body, responseType));
    }

    public <T> T patchWithAuthRetry(String endpoint, Object body, Class<T> responseType) {
        return executeWithAuthRetry(() -> restApi.patch(endpoint, body, responseType));
    }

    public <T> T deleteWithAuthRetry(String endpoint, Class<T> responseType) {
        return executeWithAuthRetry(() -> restApi.delete(endpoint, responseType));
    }

    private <T> T executeWithAuthRetry(ApiCall<T> apiCall) {
        try {
            return apiCall.execute();
        } catch (HttpClientException e) {
            if (e.getStatusCode() != 401) {
                throw e;
            }
            HttpClientException rejected = e;
            String cached = accessTokenSupplier.get();
            if (cached == null) {
                // the supplier could not obtain a token at all: the renewer would repeat the same failing
                // refresh (and could signal connection lost a second time)
                throw e;
            }
            if (!cached.equals(bearerToken)) {
                // lazy bootstrap, or another caller already renewed: try the cached token before renewing
                applyToken(cached);
                try {
                    return apiCall.execute();
                } catch (HttpClientException cachedTokenFailure) {
                    if (cachedTokenFailure.getStatusCode() != 401) {
                        throw cachedTokenFailure;
                    }
                    rejected = cachedTokenFailure;
                }
            }
            String renewed = accessTokenRenewer.get();
            if (renewed == null || renewed.equals(bearerToken)) {
                // no token, or a renewal cooldown loser handed back the token that was just rejected:
                // a third attempt would fail for sure
                throw rejected;
            }
            applyToken(renewed);
            return apiCall.execute();
        }
    }

    private void applyToken(String token) {
        bearerToken = token;
        restApi.setBearerToken(token);
    }

    private interface ApiCall<T> {
        T execute();
    }

}
