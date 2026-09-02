package pl.commercelink.rest.client;

import java.util.Map;
import java.util.function.Supplier;

public class RestApiWithRetry {

    private final RestApi restApi;

    // kept for the legacy constructor and future eager token bootstrap
    private final Supplier<String> accessTokenSupplier;

    private final Supplier<String> accessTokenRenewer;

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
            String renewed = accessTokenRenewer.get();
            if (renewed == null) {
                throw e;
            }
            restApi.setBearerToken(renewed);
            return apiCall.execute();
        }
    }

    private interface ApiCall<T> {
        T execute();
    }

}
