package pl.commercelink.rest.client;

import java.util.Map;
import java.util.function.Supplier;

public class RestApiWithRetry {

    private final RestApi restApi;

    private final Supplier<String> accessTokenSupplier;

    public RestApiWithRetry(RestApi restApi, Supplier<String> accessTokenSupplier) {
        this.restApi = restApi;
        this.accessTokenSupplier = accessTokenSupplier;
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

    public <T> void deleteWithAuthRetry(String endpoint) {
        executeWithAuthRetry(() -> restApi.delete(endpoint, Void.class));
    }

    private <T> T executeWithAuthRetry(ApiCall<T> apiCall) {
        try {
            return apiCall.execute();
        } catch (HttpClientException e) {
            if (e.getStatusCode() == 401) {
                restApi.setBearerToken(accessTokenSupplier.get());
                return apiCall.execute();
            } else {
                throw e;
            }
        }
    }

    private interface ApiCall<T> {
        T execute();
    }

}
