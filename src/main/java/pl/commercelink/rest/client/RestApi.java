package pl.commercelink.rest.client;

import com.fasterxml.jackson.core.type.TypeReference;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class RestApi {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final JsonHttpClient httpClient;

    private final String baseUrl;
    private String bearerToken;

    private final Map<String, String> defaultHeaders;
    private final Duration requestTimeout;

    private RestApi(String baseUrl, String bearerToken, Map<String, String> defaultHeaders, Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.bearerToken = bearerToken;
        this.defaultHeaders = defaultHeaders;
        this.requestTimeout = requestTimeout != null ? requestTimeout : DEFAULT_REQUEST_TIMEOUT;
        this.httpClient = new JsonHttpClient();
    }

    public RestApi(String baseUrl) {
        this(baseUrl, null, new HashMap<>(), DEFAULT_REQUEST_TIMEOUT);
    }

    public <T> T fetch(String endpoint, Class<T> responseType) {
        return fetch(endpoint, new HashMap<>(), responseType);
    }

    public <T> T fetch(String endpoint, Map<String, String> params, Class<T> responseType) {
        return fetch(endpoint, params, Map.of(), responseType);
    }

    /** Headers given here override default headers with the same name for this request only. */
    public <T> T fetch(String endpoint, Map<String, String> params, Map<String, String> headers,
                       Class<T> responseType) {
        return execute(buildGet(endpoint, params, headers), responseType);
    }

    public <T> List<T> fetchList(String endpoint, Map<String, String> params, TypeReference<List<T>> responseType) {
        HttpRequest request = buildRequest(buildUrl(endpoint, params))
                .GET()
                .build();

        return httpClient.sendAndParse(request, responseType);
    }

    public <T> T post(String endpoint, Object body, Class<T> responseType) {
        return post(endpoint, body, Map.of(), responseType);
    }

    /** Headers given here override default headers with the same name for this request only. */
    public <T> T post(String endpoint, Object body, Map<String, String> headers, Class<T> responseType) {
        return execute(buildPost(endpoint, body, headers), responseType);
    }

    public <T> T put(String endpoint, Object body, Class<T> responseType) {
        return execute(buildPut(endpoint, body), responseType);
    }

    public <T> T patch(String endpoint, Object body, Class<T> responseType) {
        return execute(buildPatch(endpoint, body), responseType);
    }

    HttpRequest buildGet(String endpoint, Map<String, String> params, Map<String, String> headers) {
        return buildRequest(buildUrl(endpoint, params), headers)
                .GET()
                .build();
    }

    HttpRequest buildPost(String endpoint, Object body) {
        return buildPost(endpoint, body, Map.of());
    }

    HttpRequest buildPost(String endpoint, Object body, Map<String, String> headers) {
        return withContentType(buildRequest(baseUrl + endpoint, headers), headers)
                .POST(httpClient.jsonBodyPublisher(body))
                .build();
    }

    HttpRequest buildPut(String endpoint, Object body) {
        return withContentType(buildRequest(baseUrl + endpoint, Map.of()), Map.of())
                .PUT(httpClient.jsonBodyPublisher(body))
                .build();
    }

    HttpRequest buildPatch(String endpoint, Object body) {
        return withContentType(buildRequest(baseUrl + endpoint, Map.of()), Map.of())
                .method("PATCH", httpClient.jsonBodyPublisher(body))
                .build();
    }

    private HttpRequest.Builder withContentType(HttpRequest.Builder builder, Map<String, String> headers) {
        if (!mergeHeaders(headers).containsKey("Content-Type")) {
            builder.header("Content-Type", "application/json");
        }
        return builder;
    }

    // HttpRequest.Builder.header() appends, so defaults and per-request headers are merged first and
    // every header is added exactly once; per-request values win, case-insensitively.
    private Map<String, String> mergeHeaders(Map<String, String> headers) {
        Map<String, String> merged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        merged.putAll(defaultHeaders);
        merged.putAll(headers);
        return merged;
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
        return buildRequest(url, Map.of());
    }

    private HttpRequest.Builder buildRequest(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout);

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        Map<String, String> effective = mergeHeaders(headers);
        if (!effective.containsKey("Accept")) {
            builder.header("Accept", "application/json");
        }
        for (Map.Entry<String, String> entry : effective.entrySet()) {
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

    void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }

    public static class Builder {
        private final String baseUrl;
        private String bearerToken;
        private final Map<String, String> defaultHeaders = new HashMap<>();
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;

        private Builder(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Builder bearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
            return this;
        }

        public Builder defaultHeader(String key, String value) {
            this.defaultHeaders.put(key, value);
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public RestApi build() {
            return new RestApi(baseUrl, bearerToken, new HashMap<>(defaultHeaders), requestTimeout);
        }
    }

}
