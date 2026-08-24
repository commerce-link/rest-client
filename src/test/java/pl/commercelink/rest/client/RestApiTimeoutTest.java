package pl.commercelink.rest.client;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestApiTimeoutTest {

    @Test
    void requestTimesOutWhenServerNeverResponds() throws Exception {
        // A bound ServerSocket accepts the TCP connection into its backlog
        // but never reads or responds, so only the request timeout can fire.
        try (ServerSocket server = new ServerSocket(0)) {
            RestApi api = RestApi.builder("http://localhost:" + server.getLocalPort())
                    .requestTimeout(Duration.ofMillis(250))
                    .build();

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> api.fetch("/never", String.class));

            assertInstanceOf(HttpTimeoutException.class, exception.getCause());
        }
    }
}
