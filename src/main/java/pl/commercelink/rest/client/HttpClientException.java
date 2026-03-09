package pl.commercelink.rest.client;

public class HttpClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public HttpClientException(int statusCode, String responseBody) {
        super("HTTP " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

}
