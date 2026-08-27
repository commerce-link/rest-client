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

    @Test
    void perRequestHeaderOverridesDefaultWithoutDuplicating() {
        // given
        RestApi restApi = RestApi.builder("https://api.example.com")
                .defaultHeader("Accept", "application/vnd.allegro.public.v1+json")
                .defaultHeader("Content-Type", "application/vnd.allegro.public.v1+json")
                .build();

        // when
        HttpRequest request = restApi.buildPost("/order/customer-returns/1/rejection", Map.of(),
                Map.of("Accept", "application/vnd.allegro.beta.v1+json",
                        "Content-Type", "application/vnd.allegro.beta.v1+json"));

        // then
        assertEquals(List.of("application/vnd.allegro.beta.v1+json"), request.headers().allValues("Accept"));
        assertEquals(List.of("application/vnd.allegro.beta.v1+json"), request.headers().allValues("Content-Type"));
    }

    @Test
    void perRequestHeaderKeepsOtherDefaults() {
        // given
        RestApi restApi = RestApi.builder("https://api.example.com")
                .defaultHeader("Accept", "application/vnd.allegro.public.v1+json")
                .defaultHeader("X-Trace", "abc")
                .build();

        // when
        HttpRequest request = restApi.buildGet("/order/customer-returns", Map.of("limit", "10"),
                Map.of("Accept", "application/vnd.allegro.beta.v1+json"));

        // then
        assertEquals(List.of("application/vnd.allegro.beta.v1+json"), request.headers().allValues("Accept"));
        assertEquals(List.of("abc"), request.headers().allValues("X-Trace"));
        assertEquals("https://api.example.com/order/customer-returns?limit=10", request.uri().toString());
    }

    @Test
    void emptyPerRequestHeadersFallBackToDefaults() {
        // given
        RestApi restApi = RestApi.builder("https://api.example.com").build();

        // when
        HttpRequest request = restApi.buildPost("/orders", Map.of(), Map.of());

        // then
        assertEquals(List.of("application/json"), request.headers().allValues("Accept"));
        assertEquals(List.of("application/json"), request.headers().allValues("Content-Type"));
    }
}
