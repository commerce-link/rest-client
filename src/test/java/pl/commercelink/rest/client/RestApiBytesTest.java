package pl.commercelink.rest.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestApiBytesTest {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4', (byte) 0xE2, (byte) 0xE3};

    private HttpServer server;
    private final AtomicReference<String> acceptSeen = new AtomicReference<>();
    private final AtomicReference<String> methodSeen = new AtomicReference<>();
    private final AtomicReference<String> contentTypeSeen = new AtomicReference<>();
    private final AtomicReference<String> bodySeen = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/packages/1/label", exchange -> {
            acceptSeen.set(exchange.getRequestHeaders().getFirst("Accept"));
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, PDF.length);
            exchange.getResponseBody().write(PDF);
            exchange.close();
        });
        server.createContext("/packages/2/label", exchange -> {
            byte[] body = "{\"errors\":[{\"message\":\"Brak etykiety\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/shipment-management/label", exchange -> {
            methodSeen.set(exchange.getRequestMethod());
            acceptSeen.set(exchange.getRequestHeaders().getFirst("Accept"));
            contentTypeSeen.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            bodySeen.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, PDF.length);
            exchange.getResponseBody().write(PDF);
            exchange.close();
        });
        server.createContext("/shipment-management/label-missing", exchange -> {
            byte[] body = "{\"errors\":[{\"code\":\"SHIPMENT_NOT_FOUND\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private RestApi api() {
        return RestApi.builder("http://localhost:" + server.getAddress().getPort()).build();
    }

    @Test
    void fetchBytesReturnsTheBodyUntouchedWithItsContentType() {
        // when
        BinaryResponse response = api().fetchBytes("/packages/1/label", Map.of(), "application/pdf, text/plain");

        // then
        assertArrayEquals(PDF, response.content());
        assertEquals("application/pdf", response.contentType());
        assertEquals("application/pdf, text/plain", acceptSeen.get());
    }

    @Test
    void fetchBytesThrowsHttpClientExceptionWithTheTextBodyOnError() {
        // when
        HttpClientException e = assertThrows(HttpClientException.class,
                () -> api().fetchBytes("/packages/2/label", Map.of(), "application/pdf"));

        // then
        assertEquals(404, e.getStatusCode());
        assertEquals("{\"errors\":[{\"message\":\"Brak etykiety\"}]}", e.getResponseBody());
    }

    @Test
    void postForBytesSendsTheJsonBodyAndReturnsTheBytesUntouched() {
        // when
        BinaryResponse response = api().postForBytes("/shipment-management/label",
                Map.of("shipmentIds", java.util.List.of("s-1"), "pageSize", "A6"),
                Map.of("Accept", "application/octet-stream"));

        // then
        assertEquals("POST", methodSeen.get());
        assertEquals("application/octet-stream", acceptSeen.get());
        assertEquals("application/json", contentTypeSeen.get());
        assertTrue(bodySeen.get().contains("\"shipmentIds\":[\"s-1\"]"));
        assertTrue(bodySeen.get().contains("\"pageSize\":\"A6\""));
        assertArrayEquals(PDF, response.content());
        assertEquals("application/octet-stream", response.contentType());
    }

    @Test
    void postForBytesKeepsAVendorContentTypeFromDefaultHeaders() {
        // given: Allegro clients send their vendor media type as a default header
        RestApi allegroLike = RestApi.builder("http://localhost:" + server.getAddress().getPort())
                .defaultHeader("Content-Type", "application/vnd.allegro.public.v1+json")
                .defaultHeader("Accept", "application/vnd.allegro.public.v1+json")
                .build();

        // when
        allegroLike.postForBytes("/shipment-management/label", Map.of("shipmentIds", java.util.List.of("s-1")),
                Map.of("Accept", "application/octet-stream"));

        // then: the per-request Accept wins, the default Content-Type stays
        assertEquals("application/octet-stream", acceptSeen.get());
        assertEquals("application/vnd.allegro.public.v1+json", contentTypeSeen.get());
    }

    @Test
    void postForBytesThrowsHttpClientExceptionWithTheTextBodyOnError() {
        // when
        HttpClientException e = assertThrows(HttpClientException.class,
                () -> api().postForBytes("/shipment-management/label-missing", Map.of(), Map.of()));

        // then
        assertEquals(404, e.getStatusCode());
        assertEquals("{\"errors\":[{\"code\":\"SHIPMENT_NOT_FOUND\"}]}", e.getResponseBody());
    }
}
