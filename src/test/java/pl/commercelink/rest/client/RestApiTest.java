package pl.commercelink.rest.client;

import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestApiTest {

    @Test
    void postUsesDefaultContentTypeHeaderWhenConfigured() {
        // given
        RestApi restApi = RestApi.builder("https://api.example.com")
                .defaultHeader("Content-Type", "application/vnd.allegro.public.v1+json")
                .build();

        // when
        HttpRequest request = restApi.buildPost("/orders", Map.of());

        // then
        assertEquals(List.of("application/vnd.allegro.public.v1+json"),
                request.headers().allValues("Content-Type"));
    }

    @Test
    void postDefaultsToApplicationJsonContentType() {
        // given
        RestApi restApi = RestApi.builder("https://api.example.com").build();

        // when
        HttpRequest request = restApi.buildPost("/orders", Map.of());

        // then
        assertEquals(List.of("application/json"), request.headers().allValues("Content-Type"));
    }

    @Test
    void putUsesDefaultContentTypeHeaderWhenConfigured() {
        // given
        RestApi restApi = RestApi.builder("https://api.example.com")
                .defaultHeader("Content-Type", "application/vnd.allegro.public.v1+json")
                .build();

        // when
        HttpRequest request = restApi.buildPut("/orders/1", Map.of());

        // then
        assertEquals(List.of("application/vnd.allegro.public.v1+json"),
                request.headers().allValues("Content-Type"));
    }

    @Test
    void patchUsesDefaultContentTypeHeaderWhenConfigured() {
        // given
        RestApi restApi = RestApi.builder("https://api.example.com")
                .defaultHeader("Content-Type", "application/vnd.allegro.public.v1+json")
                .build();

        // when
        HttpRequest request = restApi.buildPatch("/sale/product-offers/1", Map.of());

        // then
        assertEquals("PATCH", request.method());
        assertEquals(List.of("application/vnd.allegro.public.v1+json"),
                request.headers().allValues("Content-Type"));
    }

    @Test
    void patchDefaultsToApplicationJsonContentType() {
        // given
        RestApi restApi = RestApi.builder("https://api.example.com").build();

        // when
        HttpRequest request = restApi.buildPatch("/sale/product-offers/1", Map.of());

        // then
        assertEquals("PATCH", request.method());
        assertEquals(List.of("application/json"), request.headers().allValues("Content-Type"));
    }
}
