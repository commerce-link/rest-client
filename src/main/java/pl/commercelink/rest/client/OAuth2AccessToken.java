package pl.commercelink.rest.client;

import java.time.Instant;

public class OAuth2AccessToken extends AbstractOAuth2Token {

    private OAuth2AccessToken () {

    }

    public OAuth2AccessToken(String tokenValue, Instant issuedAt, Instant expiresAt) {
        super(tokenValue, issuedAt, expiresAt);
    }
}
