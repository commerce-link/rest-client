package pl.commercelink.rest.client;

/** A response body kept as bytes (a PDF label, a ZPL file) together with the Content-Type the server sent. */
public record BinaryResponse(byte[] content, String contentType) {
}
