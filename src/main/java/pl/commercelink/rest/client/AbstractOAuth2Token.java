package pl.commercelink.rest.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AbstractOAuth2Token {

    @JsonProperty("tokenValue")
    private String tokenValue;

    @JsonProperty("issuedAt")
    private Instant issuedAt;

    @JsonProperty("expiresAt")
    private Instant expiresAt;

    public AbstractOAuth2Token() {
    }

    public AbstractOAuth2Token(String tokenValue, Instant issuedAt, Instant expiresAt) {
        this.tokenValue = tokenValue;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(java.time.Instant.now().plusSeconds(60));
    }

}
