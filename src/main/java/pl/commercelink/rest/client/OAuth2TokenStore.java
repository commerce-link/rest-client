package pl.commercelink.rest.client;

import java.util.Optional;

public interface OAuth2TokenStore {

    <T> Optional<T> getToken(String key, String tokenName, String tokenType, Class<T> clazz);

    void storeToken(String key, String tokenName, String tokenType, Object token);

}
