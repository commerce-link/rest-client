package pl.commercelink.rest.client;

import java.time.Instant;

class OAuth2RefreshToken extends AbstractOAuth2Token {

    private OAuth2RefreshToken () {

    }

    public OAuth2RefreshToken(String tokenValue, Instant issuedAt, Instant expiresAt) {
        super(tokenValue, issuedAt, expiresAt);
    }

}
