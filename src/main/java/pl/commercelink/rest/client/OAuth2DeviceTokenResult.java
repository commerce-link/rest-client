package pl.commercelink.rest.client;

public record OAuth2DeviceTokenResult(
        Status status,
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds,
        String error
) {

    public enum Status {
        PENDING,
        SLOW_DOWN,
        AUTHORIZED,
        FAILED
    }

    static OAuth2DeviceTokenResult pending() {
        return new OAuth2DeviceTokenResult(Status.PENDING, null, null, 0L, null);
    }

    static OAuth2DeviceTokenResult slowDown() {
        return new OAuth2DeviceTokenResult(Status.SLOW_DOWN, null, null, 0L, null);
    }

    static OAuth2DeviceTokenResult authorized(String accessToken, String refreshToken, long accessTokenExpiresInSeconds) {
        return new OAuth2DeviceTokenResult(Status.AUTHORIZED, accessToken, refreshToken, accessTokenExpiresInSeconds, null);
    }

    static OAuth2DeviceTokenResult failed(String error) {
        return new OAuth2DeviceTokenResult(Status.FAILED, null, null, 0L, error);
    }
}
