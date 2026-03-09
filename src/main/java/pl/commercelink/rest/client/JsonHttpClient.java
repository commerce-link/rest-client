package pl.commercelink.rest.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

class JsonHttpClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    JsonHttpClient() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("HTTP request failed", e);
        }
    }

    HttpResponse<String> sendAndCheckStatus(HttpRequest request) {
        HttpResponse<String> response = send(request);
        int statusCode = response.statusCode();
        if (statusCode >= 400) {
            throw new HttpClientException(statusCode, response.body());
        }
        return response;
    }

    <T> T sendAndParse(HttpRequest request, Class<T> responseType) {
        HttpResponse<String> response = sendAndCheckStatus(request);
        if (responseType == Void.class || responseType == void.class) {
            return null;
        }
        return parseJson(response.body(), responseType);
    }

    <T> T sendAndParse(HttpRequest request, TypeReference<T> responseType) {
        HttpResponse<String> response = sendAndCheckStatus(request);
        return parseJson(response.body(), responseType);
    }

    String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    <T> T parseJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON response", e);
        }
    }

    <T> T parseJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON response", e);
        }
    }

    HttpRequest.BodyPublisher jsonBodyPublisher(Object body) {
        return HttpRequest.BodyPublishers.ofString(toJson(body));
    }

}
