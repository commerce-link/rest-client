package pl.commercelink.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OAuth2AuthorizationResponseTest {

    @Test
    void ignoresUnknownFieldsInTokenResponse() throws Exception {
        // given
        String body = """
                {
                  "access_token": "at-123",
                  "token_type": "bearer",
                  "refresh_token": "rt-456",
                  "expires_in": 43199,
                  "scope": "allegro:api:orders:read allegro:api:orders:write",
                  "allegro_api": true,
                  "jti": "a1b2c3"
                }
                """;

        // when
        OAuth2AuthorizationResponse response = new ObjectMapper().readValue(body, OAuth2AuthorizationResponse.class);

        // then
        assertEquals("at-123", response.getAccessToken());
        assertEquals("rt-456", response.getRefreshToken());
        assertEquals(43199, response.getExpiresIn());
        assertEquals("bearer", response.getTokenType());
    }
}
