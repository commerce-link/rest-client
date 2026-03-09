package pl.commercelink.rest.client;

import com.fasterxml.jackson.core.type.TypeReference;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
    This class can't be used as a Spring Bean because it needs to be instantiated in Store context;
    Additionally, it is not thread-safe due to the mutable headers and bearerToken fields.
 */
public class RestApi {

    private final JsonHttpClient httpClient;

    private final String baseUrl;
    private String bearerToken;

    private final Map<String, String> defaultHeaders = new HashMap<>();

    public RestApi(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = new JsonHttpClient();
    }

    public <T> T fetch(String endpoint, Class<T> responseType) {
        return fetch(endpoint, new HashMap<>(), responseType);
    }

    public <T> T fetch(String endpoint, Map<String, String> params, Class<T> responseType) {
        HttpRequest request = buildRequest(buildUrl(endpoint, params))
                .GET()
                .build();

        return execute(request, responseType);
    }

    public <T> List<T> fetchList(String endpoint, Map<String, String> params, TypeReference<List<T>> responseType) {
        HttpRequest request = buildRequest(buildUrl(endpoint, params))
                .GET()
                .build();

        return httpClient.sendAndParse(request, responseType);
    }

    public <T> T post(String endpoint, Object body, Class<T> responseType) {
        HttpRequest request = buildRequest(baseUrl + endpoint)
                .POST(httpClient.jsonBodyPublisher(body))
                .header("Content-Type", "application/json")
                .build();

        return execute(request, responseType);
    }

    public <T> T put(String endpoint, Object body, Class<T> responseType) {
        HttpRequest request = buildRequest(baseUrl + endpoint)
                .PUT(httpClient.jsonBodyPublisher(body))
                .header("Content-Type", "application/json")
                .build();

        return execute(request, responseType);
    }

    public <T> T delete(String endpoint, Class<T> responseType) {
        HttpRequest request = buildRequest(baseUrl + endpoint)
                .DELETE()
                .build();

        return execute(request, responseType);
    }

    private <T> T execute(HttpRequest request, Class<T> responseType) {
        return httpClient.sendAndParse(request, responseType);
    }

    private HttpRequest.Builder buildRequest(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        if (!defaultHeaders.containsKey("Accept")) {
            builder.header("Accept", "application/json");
        }

        for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        return builder;
    }

    private String buildUrl(String endpoint, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(baseUrl).append(endpoint);
        if (!params.isEmpty()) {

            if (!baseUrl.contains("?") && !endpoint.contains("?")) {
                sb.append("?");
            } else {
                sb.append("&");
            }

            sb.append(params.entrySet()
                    .stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(Collectors.joining("&")));
        }
        return sb.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public void addDefaultHeaders(String headerKey, String headerValue) {
        defaultHeaders.put(headerKey, headerValue);
    }

    public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken; }

}
