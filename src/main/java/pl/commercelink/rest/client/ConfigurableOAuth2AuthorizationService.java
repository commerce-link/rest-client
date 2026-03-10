package pl.commercelink.rest.client;

import java.util.function.Consumer;

public class ConfigurableOAuth2AuthorizationService extends OAuth2AuthorizationService {

    private final String tokenName;
    private final String authorizationEndpoint;
    private final String refreshTokenEndpoint;
    private final long refreshTokenExpirationInSeconds;
    private final Consumer<String> connectionLostHandler;

    public ConfigurableOAuth2AuthorizationService(
            OAuth2CredentialStore credentialStore,
            OAuth2TokenStore tokenStore,
            String tokenName,
            String authorizationEndpoint,
            String refreshTokenEndpoint,
            long refreshTokenExpirationInSeconds,
            Consumer<String> connectionLostHandler) {
        super(credentialStore, tokenStore);
        this.tokenName = tokenName;
        this.authorizationEndpoint = authorizationEndpoint;
        this.refreshTokenEndpoint = refreshTokenEndpoint;
        this.refreshTokenExpirationInSeconds = refreshTokenExpirationInSeconds;
        this.connectionLostHandler = connectionLostHandler;
    }

    @Override
    protected String getTokenName() {
        return tokenName;
    }

    @Override
    protected String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    @Override
    protected String getRefreshTokenEndpoint() {
        return refreshTokenEndpoint;
    }

    @Override
    protected long getRefreshTokenExpirationInSeconds() {
        return refreshTokenExpirationInSeconds;
    }

    @Override
    protected void markConnectionAsLost(String storeId) {
        connectionLostHandler.accept(storeId);
    }
}
