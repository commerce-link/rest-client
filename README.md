# rest-client

A lightweight REST client library for building integrations with external APIs that use OAuth2 authentication.

## Features

- **HTTP client** (`RestApi`) — GET, POST, PUT, DELETE with JSON serialization, custom headers, and bearer token support
- **Automatic token refresh** (`RestApiWithRetry`) — on 401 Unauthorized, retries with the cached access token first and, if that is also rejected, with a freshly renewed one
- **OAuth2 authorization flow** (`OAuth2AuthorizationService`) — handles token acquisition, refresh, and storage via pluggable stores
- **Pluggable credential and token storage** — implement `OAuth2CredentialStore` and `OAuth2TokenStore` to use any backend (AWS, database, file, etc.)

## Dependencies

- Jackson (JSON serialization)
- Java 21+ (`java.net.http.HttpClient`)

No Spring or AWS dependencies.

## Usage

```java
// Simple REST calls
RestApi api = new RestApi("https://api.example.com");
api.setBearerToken("my-token");
MyResponse response = api.fetch("/endpoint", MyResponse.class);

// Automatic token handling on 401: the cached token is tried first, then renewed once
// authService: ConfigurableOAuth2AuthorizationService; storeId: your tenant key
RestApiWithRetry retrying = new RestApiWithRetry(
        api,
        () -> authService.getAccessToken(storeId),   // cached token (refreshes only when locally expired)
        () -> authService.renewAccessToken(storeId)); // after 401: treat the cached token as revoked and refresh
MyResponse renewedResponse = retrying.fetchWithAuthRetry("/endpoint", Map.of(), MyResponse.class);
```

Renewal runs at most once per 60 s per store and token name, a window shared across all `ConfigurableOAuth2AuthorizationService` instances in the JVM. When the refresh token is rejected and the secrets contain a username, the service falls back to the password grant; a failed fallback with a 4xx status marks the connection lost.

To use OAuth2 authorization, extend `OAuth2AuthorizationService` and provide implementations of `OAuth2TokenStore` and `OAuth2CredentialStore` for your storage backend.
