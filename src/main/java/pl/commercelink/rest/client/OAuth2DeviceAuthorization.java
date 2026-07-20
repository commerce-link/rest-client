package pl.commercelink.rest.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OAuth2DeviceAuthorization {

    @JsonProperty("device_code")
    private String deviceCode;
    @JsonProperty("user_code")
    private String userCode;
    @JsonProperty("verification_uri")
    private String verificationUri;
    @JsonProperty("verification_uri_complete")
    private String verificationUriComplete;
    @JsonProperty("expires_in")
    private long expiresIn;
    @JsonProperty("interval")
    private long interval;

    public String getDeviceCode() {
        return deviceCode;
    }

    public String getUserCode() {
        return userCode;
    }

    public String getVerificationUri() {
        return verificationUri;
    }

    public String getVerificationUriComplete() {
        return verificationUriComplete;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public long getInterval() {
        return interval;
    }
}
