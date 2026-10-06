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

class RestApiBytesTest {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4', (byte) 0xE2, (byte) 0xE3};

    private HttpServer server;
    private final AtomicReference<String> acceptSeen = new AtomicReference<>();

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
}
