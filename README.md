# rest-client

A lightweight REST client library for building integrations with external APIs that use OAuth2 authentication.

## Features

- **HTTP client** (`RestApi`) — GET, POST, PUT, DELETE with JSON serialization, custom headers, and bearer token support
- **Automatic token refresh** (`RestApiWithRetry`) — retries requests on 401 Unauthorized with a fresh access token
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

// With automatic token refresh on 401
RestApiWithRetry api = new RestApiWithRetry(restApi, () -> authService.getAccessToken(storeId));
MyResponse response = api.fetchWithAuthRetry("/endpoint", Map.of(), MyResponse.class);
```

To use OAuth2 authorization, extend `OAuth2AuthorizationService` and provide implementations of `OAuth2TokenStore` and `OAuth2CredentialStore` for your storage backend.
