package pl.commercelink.rest.client;

public interface OAuth2CredentialStore {

    void createOrUpdateSecrets(String key, String tokenName, OAuth2Secrets secrets);

    OAuth2Secrets getSecrets(String key, String tokenName);

    default void deleteSecrets(String key, String tokenName) {
    }

}
