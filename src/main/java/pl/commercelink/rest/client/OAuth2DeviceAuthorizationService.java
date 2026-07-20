package pl.commercelink.rest.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class OAuth2DeviceAuthorizationService {

    private static final String DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code";
    private static final String ERROR_AUTHORIZATION_PENDING = "authorization_pending";
    private static final String ERROR_SLOW_DOWN = "slow_down";

    private final JsonHttpClient httpClient;

    public OAuth2DeviceAuthorizationService() {
        this(new JsonHttpClient());
    }

    OAuth2DeviceAuthorizationService(JsonHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public OAuth2DeviceAuthorization startDeviceAuthorization(String deviceAuthUrl, String clientId, String clientSecret) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", clientId);
        return httpClient.sendAndParse(
                formEncodedRequest(deviceAuthUrl, params, clientId, clientSecret),
                OAuth2DeviceAuthorization.class);
    }

    public OAuth2DeviceTokenResult pollDeviceToken(String tokenUrl, String clientId, String clientSecret, String deviceCode) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", DEVICE_CODE_GRANT);
        params.put("device_code", deviceCode);
        try {
            OAuth2AuthorizationResponse response = httpClient.sendAndParse(
                    formEncodedRequest(tokenUrl, params, clientId, clientSecret),
                    OAuth2AuthorizationResponse.class);
            return OAuth2DeviceTokenResult.authorized(
                    response.getAccessToken(), response.getRefreshToken(), response.getExpiresIn());
        } catch (HttpClientException e) {
            if (e.getStatusCode() >= 500) {
                throw e;
            }
            String error = extractError(e.getResponseBody());
            if (ERROR_AUTHORIZATION_PENDING.equals(error)) {
                return OAuth2DeviceTokenResult.pending();
            }
            if (ERROR_SLOW_DOWN.equals(error)) {
                return OAuth2DeviceTokenResult.slowDown();
            }
            return OAuth2DeviceTokenResult.failed(error != null ? error : e.getResponseBody());
        }
    }

    private String extractError(String responseBody) {
        if (responseBody == null) {
            return null;
        }
        try {
            return httpClient.parseJson(responseBody, ErrorResponse.class).error();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private HttpRequest formEncodedRequest(String url, Map<String, String> params, String clientId, String clientSecret) {
        String formBody = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        String credentials = clientId + ":" + clientSecret;
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorResponse(@JsonProperty("error") String error) {
    }
}
