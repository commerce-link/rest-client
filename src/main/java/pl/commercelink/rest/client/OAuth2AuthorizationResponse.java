package pl.commercelink.rest.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OAuth2AuthorizationResponse {

    @JsonProperty("access_token")
    private  String accessToken;
    @JsonProperty("refresh_token")
    private  String refreshToken;
    @JsonProperty("expires_in")
    private  long expiresIn;
    @JsonProperty("token_type")
    private String tokenType;

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public String getTokenType() {
        return tokenType;
    }
}
