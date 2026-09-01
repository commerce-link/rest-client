package pl.commercelink.rest.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * There was no test for {@link RestApiWithRetry} in this module. These pin the 4-arg
 * fetch/post overloads: if either silently dropped the header map instead of forwarding it,
 * every caller relying on a per-request header override (e.g. Allegro's customer-returns beta
 * media type) would start getting HTTP 406 while every other module's tests stayed green.
 */
@ExtendWith(MockitoExtension.class)
class RestApiWithRetryTest {

    @Mock
    private RestApi restApi;

    private RestApiWithRetry apiWithRetry;

    @BeforeEach
    void setUp() {
        apiWithRetry = new RestApiWithRetry(restApi, () -> "token");
    }

    @Test
    void fetchWithAuthRetryPassesPerRequestHeadersThrough() {
        // given: if these overloads dropped the header map, every customer-returns call would get HTTP 406
        Map<String, String> beta = Map.of("Accept", "application/vnd.allegro.beta.v1+json");
        when(restApi.fetch("/x", Map.of(), beta, String.class)).thenReturn("ok");

        // when
        apiWithRetry.fetchWithAuthRetry("/x", Map.of(), beta, String.class);

        // then
        verify(restApi).fetch("/x", Map.of(), beta, String.class);
    }

    @Test
    void postWithAuthRetryPassesPerRequestHeadersThrough() {
        // given
        Map<String, String> beta = Map.of("Accept", "application/vnd.allegro.beta.v1+json");
        Object body = new Object();
        when(restApi.post("/x", body, beta, String.class)).thenReturn("ok");

        // when
        apiWithRetry.postWithAuthRetry("/x", body, beta, String.class);

        // then
        verify(restApi).post("/x", body, beta, String.class);
    }
}
