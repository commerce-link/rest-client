package pl.commercelink.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurableOAuth2AuthorizationServiceTest {

    @Test
    void authorizeAuthorizationLostForBadRequestAndForbidden() {
        // given / when / then
        assertTrue(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(400, false));
        assertFalse(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(400, true));
        assertTrue(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(403, false));
        assertTrue(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(403, true));
        assertFalse(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(401, false));
        assertFalse(ConfigurableOAuth2AuthorizationService.isAuthorizationLost(500, false));
    }

    @Test
    void refreshTokenRejectedForForbiddenAndInvalidGrantOnly() {
        // given / when / then
        assertTrue(ConfigurableOAuth2AuthorizationService.isRefreshTokenRejected(
                new HttpClientException(403, "{\"error\":\"forbidden\"}")));
        assertTrue(ConfigurableOAuth2AuthorizationService.isRefreshTokenRejected(
                new HttpClientException(400, "{\"error\":\"invalid_grant\"}")));
        assertFalse(ConfigurableOAuth2AuthorizationService.isRefreshTokenRejected(
                new HttpClientException(400, "{\"error\":\"invalid_request\"}")));
        assertFalse(ConfigurableOAuth2AuthorizationService.isRefreshTokenRejected(
                new HttpClientException(400, null)));
        assertFalse(ConfigurableOAuth2AuthorizationService.isRefreshTokenRejected(
                new HttpClientException(401, "{\"error\":\"invalid_grant\"}")));
    }

    @Test
    void refreshWith400InvalidGrantMarksConnectionLost() {
        // given
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2RefreshToken("refresh-token-value", Instant.now(), Instant.now().plusSeconds(3600)));
        ThrowingJsonHttpClient throwingHttpClient = new ThrowingJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_grant\"}"));
        List<String> lostConnections = new ArrayList<>();

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                throwingHttpClient,
                "allegro",
                "https://allegro.pl/auth/oauth/token",
                "https://allegro.pl/auth/oauth/token",
                3600L,
                lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertNull(accessToken);
        assertEquals(List.of(storeId), lostConnections);
    }

    @Test
    void refreshWithOther400DoesNotMarkConnectionLost() {
        // given
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2RefreshToken("refresh-token-value", Instant.now(), Instant.now().plusSeconds(3600)));
        ThrowingJsonHttpClient throwingHttpClient = new ThrowingJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_request\"}"));
        List<String> lostConnections = new ArrayList<>();

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                throwingHttpClient,
                "allegro",
                "https://allegro.pl/auth/oauth/token",
                "https://allegro.pl/auth/oauth/token",
                3600L,
                lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertNull(accessToken);
        assertTrue(lostConnections.isEmpty());
    }

    @Test
    void refreshWith403MarksConnectionLost() {
        // given
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2RefreshToken("refresh-token-value", Instant.now(), Instant.now().plusSeconds(3600)));
        ThrowingJsonHttpClient throwingHttpClient = new ThrowingJsonHttpClient(
                new HttpClientException(403, "{\"error\":\"forbidden\"}"));
        List<String> lostConnections = new ArrayList<>();

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                throwingHttpClient,
                "allegro",
                "https://allegro.pl/auth/oauth/token",
                "https://allegro.pl/auth/oauth/token",
                3600L,
                lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertNull(accessToken);
        assertEquals(List.of(storeId), lostConnections);
    }

    @Test
    void authorizeWith400WithoutUsernameMarksConnectionLost() {
        // given
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(null);
        ThrowingJsonHttpClient throwingHttpClient = new ThrowingJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_request\"}"));
        List<String> lostConnections = new ArrayList<>();

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                throwingHttpClient,
                "allegro",
                "https://allegro.pl/auth/oauth/token",
                "https://allegro.pl/auth/oauth/token",
                3600L,
                lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertNull(accessToken);
        assertEquals(List.of(storeId), lostConnections);
    }

    @Test
    void authorizeWith400WithUsernameDoesNotMarkConnectionLost() {
        // given
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret", "username", "password"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(null);
        ThrowingJsonHttpClient throwingHttpClient = new ThrowingJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_request\"}"));
        List<String> lostConnections = new ArrayList<>();

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                throwingHttpClient,
                "furgonetka",
                "https://furgonetka.pl/oauth/token",
                "https://furgonetka.pl/oauth/token",
                3600L,
                lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertNull(accessToken);
        assertTrue(lostConnections.isEmpty());
    }

    @Test
    void authorizeWith403WithUsernameMarksConnectionLost() {
        // given
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret", "username", "password"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(null);
        ThrowingJsonHttpClient throwingHttpClient = new ThrowingJsonHttpClient(
                new HttpClientException(403, "{\"error\":\"forbidden\"}"));
        List<String> lostConnections = new ArrayList<>();

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                throwingHttpClient,
                "furgonetka",
                "https://furgonetka.pl/oauth/token",
                "https://furgonetka.pl/oauth/token",
                3600L,
                lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertNull(accessToken);
        assertEquals(List.of(storeId), lostConnections);
    }

    @Test
    void successfulRefreshPersistsRotatedTokens() throws Exception {
        // given
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        long refreshTokenExpirationInSeconds = 3600L;
        FakeOAuth2CredentialStore credentialStore = new FakeOAuth2CredentialStore(
                new OAuth2Secrets("client-id", "client-secret"));
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2RefreshToken("refresh-token-value", Instant.now(), Instant.now().plusSeconds(3600)));
        OAuth2AuthorizationResponse response = new ObjectMapper().readValue(
                "{\"access_token\":\"at-new\",\"refresh_token\":\"rt-new\",\"expires_in\":43199,"
                        + "\"token_type\":\"bearer\"}",
                OAuth2AuthorizationResponse.class);
        RespondingJsonHttpClient respondingHttpClient = new RespondingJsonHttpClient(response);

        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                credentialStore,
                tokenStore,
                respondingHttpClient,
                "allegro",
                "https://allegro.pl/auth/oauth/token",
                "https://allegro.pl/auth/oauth/token",
                refreshTokenExpirationInSeconds,
                storeIdArg -> { });

        // when
        String accessToken = service.getAccessToken(storeId);

        // then
        assertEquals("at-new", accessToken);
        assertNotNull(tokenStore.storedAccessToken);
        assertEquals("at-new", ((OAuth2AccessToken) tokenStore.storedAccessToken).getTokenValue());
        assertNotNull(tokenStore.storedRefreshToken);
        OAuth2RefreshToken storedRefreshToken = (OAuth2RefreshToken) tokenStore.storedRefreshToken;
        assertEquals("rt-new", storedRefreshToken.getTokenValue());
        assertEquals(refreshTokenExpirationInSeconds * 1000,
                storedRefreshToken.getExpiresAt().toEpochMilli() - storedRefreshToken.getIssuedAt().toEpochMilli());
    }

    @Test
    void renewAccessTokenRefreshesEvenWhenTheCachedTokenIsNotExpired() throws Exception {
        // given: cached access token still valid for 30 days according to the local clock
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-revoked", Instant.now(), Instant.now().plusSeconds(2_592_000)),
                new OAuth2RefreshToken("rt-old", Instant.now(), Instant.now().plusSeconds(3600)));
        SequenceJsonHttpClient http = new SequenceJsonHttpClient(tokenResponse("at-new", "rt-new"));
        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret")),
                tokenStore, http, Clock.systemUTC(),
                "furgonetka", "https://api.furgonetka.pl/oauth/token", "https://api.furgonetka.pl/oauth/token",
                3600L, storeIdArg -> { });

        // when
        String renewed = service.renewAccessToken(storeId);

        // then: the cache is replaced, never emptied, so concurrent callers keep seeing a usable token
        assertEquals("at-new", renewed);
        assertTrue(tokenStore.deletedTokenTypes.isEmpty());
        assertEquals("at-new", ((OAuth2AccessToken) tokenStore.storedAccessToken).getTokenValue());
        assertEquals("at-new", service.getAccessToken(storeId));
        assertEquals(1, http.calls);
    }

    @Test
    void renewalCooldownLoserNeverCallsTheTokenEndpointEvenWithAnEmptyCache() throws Exception {
        // given: two service instances over the same store, and a cache that goes empty behind their back
        String storeId = "store-" + UUID.randomUUID();
        Instant start = Instant.parse("2026-09-02T12:00:00Z");
        MutableClock clock = new MutableClock(start);
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-revoked", start, start.plusSeconds(2_592_000)),
                new OAuth2RefreshToken("rt-old", start, start.plusSeconds(3600)));
        SequenceJsonHttpClient http = new SequenceJsonHttpClient(tokenResponse("at-new", "rt-new"));
        ConfigurableOAuth2AuthorizationService instanceA = furgonetkaService(tokenStore, http, clock);
        ConfigurableOAuth2AuthorizationService instanceB = furgonetkaService(tokenStore, http, clock);

        // when
        String first = instanceA.renewAccessToken(storeId);
        tokenStore.deleteToken(storeId, "furgonetka", ConfigurableOAuth2AuthorizationService.ACCESS_TOKEN);
        clock.set(start.plusSeconds(5));
        String second = instanceB.renewAccessToken(storeId);

        // then: the loser gives up instead of spending the (already rotated) refresh token
        assertEquals("at-new", first);
        assertNull(second);
        assertEquals(1, http.calls);
    }

    @Test
    void renewAccessTokenWithinCooldownReturnsCachedTokenWithoutCallingTheTokenEndpoint() throws Exception {
        // given
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        Instant start = Instant.parse("2026-09-02T12:00:00Z");
        MutableClock clock = new MutableClock(start);
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-revoked", start, start.plusSeconds(2_592_000)),
                new OAuth2RefreshToken("rt-old", start, start.plusSeconds(3600)));
        SequenceJsonHttpClient http = new SequenceJsonHttpClient(
                tokenResponse("at-new", "rt-new"), tokenResponse("at-newer", "rt-newer"));
        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret")),
                tokenStore, http, clock,
                "furgonetka", "https://api.furgonetka.pl/oauth/token", "https://api.furgonetka.pl/oauth/token",
                3600L, storeIdArg -> { });

        // when: first 401 renews, a second 401 ten seconds later must not hit the token endpoint again
        String first = service.renewAccessToken(storeId);
        clock.set(start.plusSeconds(10));
        String second = service.renewAccessToken(storeId);
        clock.set(start.plusSeconds(61));
        String third = service.renewAccessToken(storeId);

        // then
        assertEquals("at-new", first);
        assertEquals("at-new", second);
        assertEquals("at-newer", third);
        assertEquals(2, http.calls);
    }

    @Test
    void renewalCooldownIsSharedBetweenServiceInstancesForTheSameStoreAndToken() throws Exception {
        // given: the app builds a new authorization service for every provider call
        String storeId = "store-" + UUID.randomUUID();
        Instant start = Instant.parse("2026-09-02T12:00:00Z");
        MutableClock clock = new MutableClock(start);
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-revoked", start, start.plusSeconds(2_592_000)),
                new OAuth2RefreshToken("rt-old", start, start.plusSeconds(3600)));
        SequenceJsonHttpClient http = new SequenceJsonHttpClient(
                tokenResponse("at-new", "rt-new"), tokenResponse("at-newer", "rt-newer"));
        ConfigurableOAuth2AuthorizationService instanceA = furgonetkaService(tokenStore, http, clock);
        ConfigurableOAuth2AuthorizationService instanceB = furgonetkaService(tokenStore, http, clock);

        // when
        String first = instanceA.renewAccessToken(storeId);
        clock.set(start.plusSeconds(10));
        String second = instanceB.renewAccessToken(storeId);
        clock.set(start.plusSeconds(61));
        String third = instanceB.renewAccessToken(storeId);

        // then: the second renewal is throttled even though it runs on a different instance
        assertEquals("at-new", first);
        assertEquals("at-new", second);
        assertEquals("at-newer", third);
        assertEquals(2, http.calls);
    }

    @Test
    void renewalCooldownIsPerStoreAndTokenName() throws Exception {
        // given
        String firstStoreId = "store-" + UUID.randomUUID();
        String secondStoreId = "store-" + UUID.randomUUID();
        Instant start = Instant.parse("2026-09-02T12:00:00Z");
        MutableClock clock = new MutableClock(start);
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-revoked", start, start.plusSeconds(2_592_000)),
                new OAuth2RefreshToken("rt-old", start, start.plusSeconds(3600)));
        SequenceJsonHttpClient http = new SequenceJsonHttpClient(
                tokenResponse("at-1", "rt-1"), tokenResponse("at-2", "rt-2"), tokenResponse("at-3", "rt-3"));
        ConfigurableOAuth2AuthorizationService furgonetka = furgonetkaService(tokenStore, http, clock);
        ConfigurableOAuth2AuthorizationService allegro = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret")),
                tokenStore, http, clock,
                "allegro", "https://allegro.pl/auth/oauth/token", "https://allegro.pl/auth/oauth/token",
                3600L, storeIdArg -> { });

        // when: three renewals within the same minute, each for a different store/token pair
        String firstStoreToken = furgonetka.renewAccessToken(firstStoreId);
        String secondStoreToken = furgonetka.renewAccessToken(secondStoreId);
        String otherTokenName = allegro.renewAccessToken(firstStoreId);

        // then: the cooldown of one pair never throttles another
        assertEquals("at-1", firstStoreToken);
        assertEquals("at-2", secondStoreToken);
        assertEquals("at-3", otherTokenName);
        assertEquals(3, http.calls);
    }

    private static ConfigurableOAuth2AuthorizationService furgonetkaService(
            OAuth2TokenStore tokenStore, JsonHttpClient httpClient, Clock clock) {
        return new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret")),
                tokenStore, httpClient, clock,
                "furgonetka", "https://api.furgonetka.pl/oauth/token", "https://api.furgonetka.pl/oauth/token",
                3600L, storeIdArg -> { });
    }

    @Test
    void rejectedRefreshTokenFallsBackToPasswordGrantWhenUsernameIsConfigured() throws Exception {
        // given: Furgonetka revoked the whole session, so the refresh grant fails with invalid_grant
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-revoked", Instant.now(), Instant.now().plusSeconds(2_592_000)),
                new OAuth2RefreshToken("rt-revoked", Instant.now(), Instant.now().plusSeconds(3600)));
        SequenceJsonHttpClient http = new SequenceJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_grant\"}"),
                tokenResponse("at-new", "rt-new"));
        List<String> lostConnections = new ArrayList<>();
        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret", "user@example.com", "secret")),
                tokenStore, http, Clock.systemUTC(),
                "furgonetka", "https://api.furgonetka.pl/oauth/token", "https://api.furgonetka.pl/oauth/token",
                3600L, lostConnections::add);

        // when
        String renewed = service.renewAccessToken(storeId);

        // then
        assertEquals("at-new", renewed);
        assertEquals(2, http.calls);
        assertTrue(lostConnections.isEmpty());
        assertEquals("rt-new", ((OAuth2RefreshToken) tokenStore.storedRefreshToken).getTokenValue());
    }

    @Test
    void rejectedRefreshTokenWithoutUsernameStillMarksConnectionLost() {
        // given: device-flow provider (Allegro) has no password to fall back to
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-revoked", Instant.now(), Instant.now().plusSeconds(3600)),
                new OAuth2RefreshToken("rt-revoked", Instant.now(), Instant.now().plusSeconds(3600)));
        SequenceJsonHttpClient http = new SequenceJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_grant\"}"));
        List<String> lostConnections = new ArrayList<>();
        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret")),
                tokenStore, http, Clock.systemUTC(),
                "allegro", "https://allegro.pl/auth/oauth/token", "https://allegro.pl/auth/oauth/token",
                3600L, lostConnections::add);

        // when
        String renewed = service.renewAccessToken(storeId);

        // then
        assertNull(renewed);
        assertEquals(List.of(storeId), lostConnections);
        assertEquals(1, http.calls);
    }

    @Test
    void passwordGrantFallbackThatIsForbiddenMarksConnectionLost() {
        // given
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-revoked", Instant.now(), Instant.now().plusSeconds(3600)),
                new OAuth2RefreshToken("rt-revoked", Instant.now(), Instant.now().plusSeconds(3600)));
        SequenceJsonHttpClient http = new SequenceJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_grant\"}"),
                new HttpClientException(403, "{\"error\":\"access_denied\"}"));
        List<String> lostConnections = new ArrayList<>();
        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret", "user@example.com", "bad")),
                tokenStore, http, Clock.systemUTC(),
                "furgonetka", "https://api.furgonetka.pl/oauth/token", "https://api.furgonetka.pl/oauth/token",
                3600L, lostConnections::add);

        // when
        String renewed = service.renewAccessToken(storeId);

        // then
        assertNull(renewed);
        assertEquals(List.of(storeId), lostConnections);
    }

    @Test
    void passwordGrantFallbackRejectedWithInvalidGrantMarksConnectionLost() {
        // given: the store's password changed at the provider, so both grants are rejected
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-revoked", Instant.now(), Instant.now().plusSeconds(3600)),
                new OAuth2RefreshToken("rt-revoked", Instant.now(), Instant.now().plusSeconds(3600)));
        SequenceJsonHttpClient http = new SequenceJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_grant\"}"),
                new HttpClientException(400, "{\"error\":\"invalid_grant\"}"));
        List<String> lostConnections = new ArrayList<>();
        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret", "user@example.com", "old-password")),
                tokenStore, http, Clock.systemUTC(),
                "furgonetka", "https://api.furgonetka.pl/oauth/token", "https://api.furgonetka.pl/oauth/token",
                3600L, lostConnections::add);

        // when
        String renewed = service.renewAccessToken(storeId);

        // then
        assertNull(renewed);
        assertEquals(List.of(storeId), lostConnections);
        assertEquals(2, http.calls);
    }

    @Test
    void passwordGrantFallbackFailingWithServerErrorDoesNotMarkConnectionLost() {
        // given: the refresh token was rejected and the token endpoint is then down
        // unique per test: the renewal cooldown is shared static state
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-revoked", Instant.now(), Instant.now().plusSeconds(3600)),
                new OAuth2RefreshToken("rt-revoked", Instant.now(), Instant.now().plusSeconds(3600)));
        SequenceJsonHttpClient http = new SequenceJsonHttpClient(
                new HttpClientException(400, "{\"error\":\"invalid_grant\"}"),
                new HttpClientException(503, "service unavailable"));
        List<String> lostConnections = new ArrayList<>();
        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret", "user@example.com", "secret")),
                tokenStore, http, Clock.systemUTC(),
                "furgonetka", "https://api.furgonetka.pl/oauth/token", "https://api.furgonetka.pl/oauth/token",
                3600L, lostConnections::add);

        // when
        String renewed = service.renewAccessToken(storeId);

        // then: an outage must not look like a revoked account
        assertNull(renewed);
        assertTrue(lostConnections.isEmpty());
        assertEquals(2, http.calls);
    }

    @Test
    void secondRefreshWithRotatedTokenUsesStoredToken() {
        // given: another process (a marketplace import, the second instance) spends rt-old first and stores the
        // rotated pair while our refresh with rt-old is in flight, so ours is answered with invalid_grant
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2RefreshToken("rt-old", Instant.now(), Instant.now().plusSeconds(3600)));
        RotatedElsewhereJsonHttpClient http = new RotatedElsewhereJsonHttpClient(tokenStore,
                new OAuth2AccessToken("at-other", Instant.now(), Instant.now().plusSeconds(3600)),
                new OAuth2RefreshToken("rt-other", Instant.now(), Instant.now().plusSeconds(3600)));
        List<String> lostConnections = new ArrayList<>();
        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret")),
                tokenStore, http, Clock.systemUTC(),
                "allegro_marketplace", "https://allegro.pl/auth/oauth/token", "https://allegro.pl/auth/oauth/token",
                3600L, lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then: the token the other process stored is used, the connection stays
        assertEquals("at-other", accessToken);
        assertTrue(lostConnections.isEmpty());
        assertEquals(1, http.calls);
    }

    @Test
    void rotatedRefreshTokenWithExpiredAccessTokenRefreshesAgainWithTheNewToken() throws Exception {
        // given: the other process stored a new refresh token, but the access token it stored is already expired
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2RefreshToken("rt-old", Instant.now(), Instant.now().plusSeconds(3600)));
        RotatedElsewhereJsonHttpClient http = new RotatedElsewhereJsonHttpClient(tokenStore,
                new OAuth2AccessToken("at-stale", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600)),
                new OAuth2RefreshToken("rt-other", Instant.now(), Instant.now().plusSeconds(3600)),
                tokenResponse("at-new", "rt-new"));
        List<String> lostConnections = new ArrayList<>();
        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret")),
                tokenStore, http, Clock.systemUTC(),
                "allegro_marketplace", "https://allegro.pl/auth/oauth/token", "https://allegro.pl/auth/oauth/token",
                3600L, lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then: one more refresh, with the rotated token, and its pair is stored
        assertEquals("at-new", accessToken);
        assertEquals(2, http.calls);
        assertTrue(lostConnections.isEmpty());
        assertEquals("rt-new", ((OAuth2RefreshToken) tokenStore.storedRefreshToken).getTokenValue());
    }

    @Test
    void rotatedTokenRejectedAgainMarksConnectionLostOnce() {
        // given: the rotated token is rejected too: the authorization really is gone
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2RefreshToken("rt-old", Instant.now(), Instant.now().plusSeconds(3600)));
        RotatedElsewhereJsonHttpClient http = new RotatedElsewhereJsonHttpClient(tokenStore,
                new OAuth2AccessToken("at-stale", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600)),
                new OAuth2RefreshToken("rt-other", Instant.now(), Instant.now().plusSeconds(3600)),
                new HttpClientException(400, "{\"error\":\"invalid_grant\"}"));
        List<String> lostConnections = new ArrayList<>();
        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret")),
                tokenStore, http, Clock.systemUTC(),
                "allegro_marketplace", "https://allegro.pl/auth/oauth/token", "https://allegro.pl/auth/oauth/token",
                3600L, lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then: no third attempt, the loss is reported exactly once
        assertNull(accessToken);
        assertEquals(2, http.calls);
        assertEquals(List.of(storeId), lostConnections);
    }

    @Test
    void rotatedRefreshTokenIsCheckedBeforeThePasswordGrantFallback() {
        // given: Furgonetka-style secrets with a password grant; the token was simply rotated by another process
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2RefreshToken("rt-old", Instant.now(), Instant.now().plusSeconds(3600)));
        RotatedElsewhereJsonHttpClient http = new RotatedElsewhereJsonHttpClient(tokenStore,
                new OAuth2AccessToken("at-other", Instant.now(), Instant.now().plusSeconds(3600)),
                new OAuth2RefreshToken("rt-other", Instant.now(), Instant.now().plusSeconds(3600)));
        List<String> lostConnections = new ArrayList<>();
        ConfigurableOAuth2AuthorizationService service = new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret", "user@example.com", "secret")),
                tokenStore, http, Clock.systemUTC(),
                "furgonetka", "https://api.furgonetka.pl/oauth/token", "https://api.furgonetka.pl/oauth/token",
                3600L, lostConnections::add);

        // when
        String accessToken = service.getAccessToken(storeId);

        // then: no password grant (it would open a second session), the refresh token is not deleted
        assertEquals("at-other", accessToken);
        assertEquals(1, http.calls);
        assertTrue(tokenStore.deletedTokenTypes.isEmpty());
        assertTrue(lostConnections.isEmpty());
    }

    @Test
    void concurrentRefreshesFromTwoServiceInstancesSpendTheRefreshTokenOnce() throws Exception {
        // given: two service instances (the factories build one per call, e.g. a tracking poll and a marketplace
        // import) find the same expired access token; the token endpoint rotates single-use refresh tokens
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-expired", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600)),
                new OAuth2RefreshToken("rt-old", Instant.now(), Instant.now().plusSeconds(3600)));
        SingleUseRefreshTokenHttpClient http = new SingleUseRefreshTokenHttpClient("rt-old",
                tokenResponse("at-new", "rt-new"));
        List<String> lostConnections = java.util.Collections.synchronizedList(new ArrayList<>());
        ConfigurableOAuth2AuthorizationService first = allegroService(tokenStore, http, lostConnections);
        ConfigurableOAuth2AuthorizationService second = allegroService(tokenStore, http, lostConnections);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);

        try {
            // when: the first refresh is in flight at the token endpoint while the second caller arrives
            java.util.concurrent.Future<String> firstToken = executor.submit(() -> first.getAccessToken(storeId));
            assertTrue(http.firstCallInFlight.await(5, java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.atomic.AtomicReference<Thread> secondThread = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.Future<String> secondToken = executor.submit(() -> {
                secondThread.set(Thread.currentThread());
                return second.getAccessToken(storeId);
            });
            awaitBlockedOrFinished(secondThread, secondToken);
            http.releaseFirstCall.countDown();

            // then: one token request, both callers get the rotated access token, the connection stays
            assertEquals("at-new", firstToken.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("at-new", secondToken.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, http.calls.get());
            assertTrue(lostConnections.isEmpty());
        } finally {
            http.releaseFirstCall.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void renewalWaitingForAConcurrentRefreshUsesTheRotatedPair() throws Exception {
        // given: one instance refreshes an expired token while another instance renews after a 401
        String storeId = "store-" + UUID.randomUUID();
        FakeOAuth2TokenStore tokenStore = new FakeOAuth2TokenStore(
                new OAuth2AccessToken("at-expired", Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600)),
                new OAuth2RefreshToken("rt-old", Instant.now(), Instant.now().plusSeconds(3600)));
        SingleUseRefreshTokenHttpClient http = new SingleUseRefreshTokenHttpClient("rt-old",
                tokenResponse("at-new", "rt-new"));
        List<String> lostConnections = java.util.Collections.synchronizedList(new ArrayList<>());
        ConfigurableOAuth2AuthorizationService refreshing = allegroService(tokenStore, http, lostConnections);
        ConfigurableOAuth2AuthorizationService renewing = allegroService(tokenStore, http, lostConnections);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);

        try {
            // when
            java.util.concurrent.Future<String> refreshed = executor.submit(() -> refreshing.getAccessToken(storeId));
            assertTrue(http.firstCallInFlight.await(5, java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.atomic.AtomicReference<Thread> renewThread = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.Future<String> renewed = executor.submit(() -> {
                renewThread.set(Thread.currentThread());
                return renewing.renewAccessToken(storeId);
            });
            awaitBlockedOrFinished(renewThread, renewed);
            http.releaseFirstCall.countDown();

            // then: the renewal takes the pair stored by the refresh instead of spending rt-old again
            assertEquals("at-new", refreshed.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("at-new", renewed.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, http.calls.get());
            assertTrue(lostConnections.isEmpty());
        } finally {
            http.releaseFirstCall.countDown();
            executor.shutdownNow();
        }
    }

    private static ConfigurableOAuth2AuthorizationService allegroService(
            FakeOAuth2TokenStore tokenStore, JsonHttpClient http, List<String> lostConnections) {
        return new ConfigurableOAuth2AuthorizationService(
                new FakeOAuth2CredentialStore(new OAuth2Secrets("client-id", "client-secret")),
                tokenStore, http, Clock.systemUTC(),
                "allegro_marketplace", "https://allegro.pl/auth/oauth/token", "https://allegro.pl/auth/oauth/token",
                3600L, lostConnections::add);
    }

    /** Waits until the thread is blocked on a monitor (waiting for the refresh lock) or its call has finished. */
    private static void awaitBlockedOrFinished(java.util.concurrent.atomic.AtomicReference<Thread> thread,
                                               java.util.concurrent.Future<?> call) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread t = thread.get();
            if (call.isDone() || (t != null && t.getState() == Thread.State.BLOCKED)) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("the second caller neither blocked nor finished");
    }

    private static OAuth2AuthorizationResponse tokenResponse(String accessToken, String refreshToken) throws Exception {
        return new ObjectMapper().readValue(
                "{\"access_token\":\"" + accessToken + "\",\"refresh_token\":\"" + refreshToken
                        + "\",\"expires_in\":2592000,\"token_type\":\"bearer\"}",
                OAuth2AuthorizationResponse.class);
    }

    /** Test clock whose instant can be moved forward by hand. */
    private static class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static class FakeOAuth2CredentialStore implements OAuth2CredentialStore {

        private final OAuth2Secrets secrets;

        FakeOAuth2CredentialStore(OAuth2Secrets secrets) {
            this.secrets = secrets;
        }

        @Override
        public void createOrUpdateSecrets(String key, String tokenName, OAuth2Secrets secrets) {
        }

        @Override
        public OAuth2Secrets getSecrets(String key, String tokenName) {
            return secrets;
        }
    }

    private static class FakeOAuth2TokenStore implements OAuth2TokenStore {

        private volatile OAuth2AccessToken accessToken;
        private volatile OAuth2RefreshToken refreshToken;
        private volatile Object storedAccessToken;
        private volatile Object storedRefreshToken;
        private final List<String> deletedTokenTypes = new ArrayList<>();

        FakeOAuth2TokenStore(OAuth2RefreshToken refreshToken) {
            this(null, refreshToken);
        }

        FakeOAuth2TokenStore(OAuth2AccessToken accessToken, OAuth2RefreshToken refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> getToken(String key, String tokenName, String tokenType, Class<T> clazz) {
            if (ConfigurableOAuth2AuthorizationService.ACCESS_TOKEN.equals(tokenType) && accessToken != null) {
                return Optional.of((T) accessToken);
            }
            if (ConfigurableOAuth2AuthorizationService.REFRESH_TOKEN.equals(tokenType) && refreshToken != null) {
                return Optional.of((T) refreshToken);
            }
            return Optional.empty();
        }

        @Override
        public void storeToken(String key, String tokenName, String tokenType, Object token) {
            if (ConfigurableOAuth2AuthorizationService.ACCESS_TOKEN.equals(tokenType)) {
                storedAccessToken = token;
                accessToken = (OAuth2AccessToken) token;
            } else if (ConfigurableOAuth2AuthorizationService.REFRESH_TOKEN.equals(tokenType)) {
                storedRefreshToken = token;
                refreshToken = (OAuth2RefreshToken) token;
            }
        }

        @Override
        public void deleteToken(String key, String tokenName, String tokenType) {
            deletedTokenTypes.add(tokenType);
            if (ConfigurableOAuth2AuthorizationService.ACCESS_TOKEN.equals(tokenType)) {
                accessToken = null;
            } else if (ConfigurableOAuth2AuthorizationService.REFRESH_TOKEN.equals(tokenType)) {
                refreshToken = null;
            }
        }
    }

    /** Each call to sendAndParse consumes the next element: an HttpClientException is thrown, anything else returned. */
    private static class SequenceJsonHttpClient extends JsonHttpClient {

        private final java.util.Deque<Object> outcomes;
        private int calls;

        SequenceJsonHttpClient(Object... outcomes) {
            this.outcomes = new java.util.ArrayDeque<>(List.of(outcomes));
        }

        @Override
        @SuppressWarnings("unchecked")
        <T> T sendAndParse(HttpRequest request, Class<T> responseType) {
            calls++;
            Object next = outcomes.removeFirst();
            if (next instanceof HttpClientException e) {
                throw e;
            }
            return (T) next;
        }
    }

    /**
     * First call: another process stores a rotated token pair in the store and the token endpoint rejects our (now
     * spent) refresh token with invalid_grant. Later calls consume the given outcomes like {@link SequenceJsonHttpClient}.
     */
    private static class RotatedElsewhereJsonHttpClient extends JsonHttpClient {

        private final FakeOAuth2TokenStore tokenStore;
        private final OAuth2AccessToken otherAccessToken;
        private final OAuth2RefreshToken otherRefreshToken;
        private final java.util.Deque<Object> laterOutcomes;
        private int calls;

        RotatedElsewhereJsonHttpClient(FakeOAuth2TokenStore tokenStore, OAuth2AccessToken otherAccessToken,
                                       OAuth2RefreshToken otherRefreshToken, Object... laterOutcomes) {
            this.tokenStore = tokenStore;
            this.otherAccessToken = otherAccessToken;
            this.otherRefreshToken = otherRefreshToken;
            this.laterOutcomes = new java.util.ArrayDeque<>(List.of(laterOutcomes));
        }

        @Override
        @SuppressWarnings("unchecked")
        <T> T sendAndParse(HttpRequest request, Class<T> responseType) {
            calls++;
            if (calls == 1) {
                tokenStore.accessToken = otherAccessToken;
                tokenStore.refreshToken = otherRefreshToken;
                throw new HttpClientException(400, "{\"error\":\"invalid_grant\"}");
            }
            Object next = laterOutcomes.removeFirst();
            if (next instanceof HttpClientException e) {
                throw e;
            }
            return (T) next;
        }
    }

    /**
     * Token endpoint with single-use refresh tokens: the first call waits until the test releases it, so a second
     * caller can arrive while it is in flight; a refresh token that is not the current one gets invalid_grant.
     */
    private static class SingleUseRefreshTokenHttpClient extends JsonHttpClient {

        private final java.util.concurrent.CountDownLatch firstCallInFlight = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch releaseFirstCall = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        private final OAuth2AuthorizationResponse rotated;
        private volatile String validRefreshToken;

        SingleUseRefreshTokenHttpClient(String validRefreshToken, OAuth2AuthorizationResponse rotated) {
            this.validRefreshToken = validRefreshToken;
            this.rotated = rotated;
        }

        @Override
        @SuppressWarnings("unchecked")
        <T> T sendAndParse(HttpRequest request, Class<T> responseType) {
            int call = calls.incrementAndGet();
            String sentToken = sentRefreshToken(request);
            if (call == 1) {
                firstCallInFlight.countDown();
                try {
                    releaseFirstCall.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            synchronized (this) {
                if (!sentToken.equals(validRefreshToken)) {
                    throw new HttpClientException(400, "{\"error\":\"invalid_grant\"}");
                }
                validRefreshToken = rotated.getRefreshToken();
                return (T) rotated;
            }
        }

        private static String sentRefreshToken(HttpRequest request) {
            java.util.concurrent.CompletableFuture<String> body = new java.util.concurrent.CompletableFuture<>();
            request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<>() {
                private final StringBuilder text = new StringBuilder();

                @Override
                public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(java.nio.ByteBuffer item) {
                    text.append(java.nio.charset.StandardCharsets.UTF_8.decode(item));
                }

                @Override
                public void onError(Throwable throwable) {
                    body.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    body.complete(text.toString());
                }
            });
            String form = body.join();
            return java.util.Arrays.stream(form.split("&"))
                    .filter(pair -> pair.startsWith("refresh_token="))
                    .map(pair -> pair.substring("refresh_token=".length()))
                    .findFirst()
                    .orElse("");
        }
    }

    private static class ThrowingJsonHttpClient extends JsonHttpClient {

        private final HttpClientException exception;

        ThrowingJsonHttpClient(HttpClientException exception) {
            this.exception = exception;
        }

        @Override
        <T> T sendAndParse(HttpRequest request, Class<T> responseType) {
            throw exception;
        }
    }

    private static class RespondingJsonHttpClient extends JsonHttpClient {

        private final OAuth2AuthorizationResponse response;

        RespondingJsonHttpClient(OAuth2AuthorizationResponse response) {
            this.response = response;
        }

        @Override
        @SuppressWarnings("unchecked")
        <T> T sendAndParse(HttpRequest request, Class<T> responseType) {
            return (T) response;
        }
    }
}
