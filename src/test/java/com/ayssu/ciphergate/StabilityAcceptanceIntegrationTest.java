package com.ayssu.ciphergate;

import com.ayssu.ciphergate.service.PublicAbuseGuard;
import com.ayssu.ciphergate.service.mail.SystemSmtpMailService;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartySignatureVerifier;
import com.ayssu.ciphergate.thirdparty.dto.ThirdPartyIntegrationRequest;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyIntegrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real HTTP, security filters, Redis OTP/replay protection and committed MySQL writes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.redis.database=14")
@ActiveProfiles("test")
@Tag("integration")
class StabilityAcceptanceIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @MockitoBean SystemSmtpMailService mail;
    @MockitoBean com.ayssu.ciphergate.service.MinioObjectService objects;
    @Autowired com.ayssu.ciphergate.config.MinioProperties minio;
    @Autowired com.ayssu.ciphergate.thirdparty.service.ThirdPartyUpdateDownloadTicketService tickets;
    final ObjectMapper json = new ObjectMapper();
    final HttpClient client = HttpClient.newHttpClient();
    final Set<String> redisKeys = new HashSet<>();
    long owner, app, otherApp, credential;
    String fixture, email, username, apiKey, secret, deliveredCode;
    static final String PASSWORD = "Acceptance123!";

    @BeforeAll static void isolatedDatabaseOnly() {
        String url = System.getenv("CIPHERGATE_BOOT_JDBC_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/ciphergate_boot_test(?:\\?.*)?"))
            throw new IllegalStateException("Isolated ciphergate_boot_test database required");
    }

    @BeforeEach void fixture() {
        // Real HTTP shares loopback IP; DB 14 belongs to this suite, DB 15 to boot tests.
        // Remove only this suite's fixed source counters so local reruns remain independent.
        resetSourceCounters();
        fixture = "accept_" + UUID.randomUUID().toString().replace("-", "");
        // Explicit IDs keep cleanup isolated from other tests and existing boot fixtures.
        owner = 100_000_000L + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000_000L);
        app = owner + 1; otherApp = owner + 2; credential = owner + 3;
        username = fixture; email = fixture + "@example.test";
        apiKey = fixture; secret = "test-only-" + fixture;
        jdbc.update("INSERT INTO users(id,github_id,login) VALUES (?,?,?)", owner, fixture, fixture);
        Long level = jdbc.queryForObject("SELECT MIN(id) FROM membership_level", Long.class);
        jdbc.update("INSERT INTO user_membership(user_id,level_id,invite_code,extra_user_register_quota) VALUES (?,?,?,100)", owner, level, fixture.substring(0, 19));
        for (long id : new long[]{app, otherApp})
            jdbc.update("INSERT INTO application(id,owner_id,app_name,app_key,app_secret,status,business_model) VALUES (?,?,?,?,?,1,3)", id, owner, fixture, fixture + id, secret);
        jdbc.update("INSERT INTO third_party_credential(id,app_id,name,api_key,api_secret,allowed_ips,allow_trial,allow_query) VALUES (?,?,?,?,?,'127.0.0.1',1,1)", credential, app, fixture, apiKey, secret);
        jdbc.update("INSERT INTO third_party_credential_app(credential_id,app_id) VALUES (?,?)", credential, app);
        when(mail.isMailEnabledAndConfigured()).thenReturn(true);
        doAnswer(call -> {
            var matcher = Pattern.compile("验证码：(\\d{6})").matcher(call.getArgument(2, String.class));
            assertTrue(matcher.find()); deliveredCode = matcher.group(1); return null;
        }).when(mail).sendPlainText(eq(email), anyString(), anyString());
    }

    @AfterEach void cleanup() {
        if (app == 0) return;
        for (String table : List.of("portal_payment_order", "portal_login_log", "app_user_trial", "app_user_license_recharge", "third_party_recharge_log", "license_key", "app_user"))
            jdbc.update("DELETE FROM " + table + " WHERE app_id IN (?,?)", app, otherApp);
        jdbc.update("DELETE FROM third_party_credential_app WHERE credential_id=?", credential);
        jdbc.update("DELETE FROM third_party_credential WHERE id=?", credential);
        jdbc.update("DELETE FROM application WHERE owner_id=?", owner);
        jdbc.update("DELETE FROM activity_log WHERE user_id=?", owner);
        jdbc.update("DELETE FROM user_membership WHERE user_id=?", owner);
        jdbc.update("DELETE FROM users WHERE id=?", owner);
        for (String pattern : List.of("cg:appuser:reg:*:" + app + ":*", "cg:tp:integration:nonce:" + credential + ":*", "cg:tp:recharge:daily:" + credential + ":*", "cg:public:user:redeem:" + app + ":*")) {
            Set<String> keys = redis.keys(pattern);
            if (keys != null) redisKeys.addAll(keys);
        }
        for (String scope : List.of("portal-login-account", "register-mail-recipient"))
            redisKeys.add("abuse:" + scope + ":" + PublicAbuseGuard.digest(email));
        for (String scope : List.of("register-submit-email", "public-register-email-subject"))
            redisKeys.add("abuse:" + scope + ":" + PublicAbuseGuard.digest(app + ":" + email));
        redisKeys.add("abuse:tp-integration-key:" + PublicAbuseGuard.digest(Long.toString(credential)));
        if (!redisKeys.isEmpty()) redis.delete(redisKeys);
        resetSourceCounters();
    }

    void resetSourceCounters() {
        var keys = new ArrayList<String>();
        for (String scope : List.of("register-submit-ip", "register-mail-ip", "public-challenge",
                "public-register-email-ip", "portal-captcha", "portal-login-ip", "tp-integration-ip"))
            keys.add("abuse:" + scope + ":" + PublicAbuseGuard.digest("127.0.0.1"));
        keys.add("abuse:all-mail:" + PublicAbuseGuard.digest("global"));
        redis.delete(keys);
    }

    @Test void registrationLoginTrialRechargeAndAuthorizationFormOneCommittedJourney() throws Exception {
        String code = requestRegistrationCode();
        assertCode(400, register(code.equals("000000") ? "000001" : "000000"));
        assertEquals(0, count("app_user"));
        assertCode(200, register(code));
        assertCode(400, register(code));
        assertEquals(1, count("app_user"));
        String hash = jdbc.queryForObject("SELECT password FROM app_user WHERE app_id=?", String.class, app);
        assertNotEquals(PASSWORD, hash);
        assertTrue(cn.hutool.crypto.digest.BCrypt.checkpw(PASSWORD, hash));
        var login = login(PASSWORD, false);
        assertCode(200, login);
        String token = login.path("data").path("token").asText();
        assertFalse(token.isBlank());
        var profile = get("/api/portal/settings/profile", token);
        assertCode(200, profile);
        assertFalse(profile.toString().contains(hash));
        assertFalse(profile.path("data").has("password"));

        assertTrue(integration(ThirdPartyIntegrationService.Action.AUTH_QUERY).path("data").path("expiresAt").isNull());
        var trial = integration(ThirdPartyIntegrationService.Action.TRIAL_APPLY);
        assertCode(200, trial);
        // MySQL DATETIME persists seconds; compare subsequent entitlement changes against committed data.
        LocalDateTime trialExpiry = jdbc.queryForObject("SELECT member_expires_at FROM app_user WHERE app_id=?", LocalDateTime.class, app);
        assertTrue(java.time.Duration.between(trialExpiry, LocalDateTime.parse(trial.path("data").path("expiresAt").asText())).abs().compareTo(java.time.Duration.ofSeconds(1)) < 0);
        assertCode(409, integration(ThirdPartyIntegrationService.Action.TRIAL_APPLY));
        assertEquals(1, count("app_user_trial"));
        String card = insertCard(app);
        var redeem = redeem(card);
        assertCode(200, redeem);
        assertFalse(redeem.path("data").has("keyCode"));
        assertCode(400, redeem(card));
        assertCode(400, redeem(insertCard(otherApp)));
        assertEquals(1, count("app_user_license_recharge"));
        var query = integration(ThirdPartyIntegrationService.Action.AUTH_QUERY);
        assertCode(200, query);
        assertEquals(trialExpiry.plusDays(7), LocalDateTime.parse(query.path("data").path("expiresAt").asText()));
        assertEquals(trialExpiry.plusDays(7), jdbc.queryForObject("SELECT member_expires_at FROM app_user WHERE app_id=?", LocalDateTime.class, app));
    }

    @Test void registrationAndLoginRejectInvalidOrAlreadyConsumedProofs() throws Exception {
        String code = requestRegistrationCode();
        assertCode(400, register(code.equals("000000") ? "000001" : "000000"));
        assertCode(200, register(code));
        // Consumed OTP cannot create a second identity even with otherwise valid fields.
        var second = new LinkedHashMap<>(registration(code));
        second.put("username", username + "2"); second.put("email", "other_" + email);
        assertCode(400, post("/api/public/app-user/register/submit", second));
        assertEquals(1, count("app_user"));
        assertCode(400, login(PASSWORD, true));
        assertCode(400, login("WrongPassword123", false));
        assertEquals(0, count("portal_login_log"));
        assertCode(200, login(PASSWORD, false));
        assertEquals(1, count("portal_login_log"));
    }

    @Test void independentCredentialEnforcesIpPermissionSignatureAndReplayBeforeEntitlementWrites() throws Exception {
        assertCode(200, register(requestRegistrationCode()));
        jdbc.update("UPDATE third_party_credential SET allowed_ips='192.0.2.100' WHERE id=?", credential);
        assertCode(403, integration(ThirdPartyIntegrationService.Action.TRIAL_APPLY));
        assertEquals("IP_DENIED", lastError());
        jdbc.update("UPDATE third_party_credential SET allowed_ips='127.0.0.1',allow_trial=0 WHERE id=?", credential);
        assertCode(403, integration(ThirdPartyIntegrationService.Action.TRIAL_APPLY));
        assertEquals("OPERATION_DENIED", lastError());
        jdbc.update("UPDATE third_party_credential SET allow_trial=1 WHERE id=?", credential);
        var wrongAction = envelope(ThirdPartyIntegrationService.Action.AUTH_QUERY);
        assertCode(401, post("/api/v1/third_party/trial", wrongAction));
        assertEquals("BAD_SIGN", lastError());
        assertEquals(0, count("app_user_trial"));
        var request = envelope(ThirdPartyIntegrationService.Action.TRIAL_APPLY);
        assertCode(200, post("/api/v1/third_party/trial", request));
        assertCode(409, post("/api/v1/third_party/trial", request));
        assertEquals("REPLAY", lastError());
        assertEquals(1, count("app_user_trial"));
        assertEquals(1L, jdbc.queryForObject("SELECT used_call_count FROM third_party_credential WHERE id=?", Long.class, credential));
        assertEquals("127.0.0.1", jdbc.queryForObject("SELECT request_ip FROM third_party_recharge_log WHERE credential_id=? ORDER BY id DESC LIMIT 1", String.class, credential));
    }

    @Test void equalTimestampOrdersHaveStableDisjointPagesAndRemainScopedToLoggedInAccount() throws Exception {
        assertCode(200, register(requestRegistrationCode()));
        var authenticated = login(PASSWORD, false);
        assertCode(200, authenticated);
        String token = authenticated.path("data").path("token").asText();
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE app_id=?", Long.class, app);
        jdbc.update("INSERT INTO app_user(id,app_id,username,email) VALUES (?,?,?,?),(?,?,?,?)",
                owner + 10, otherApp, username, email, owner + 11, app, username + "2", "other_" + email);
        LocalDateTime sameSecond = LocalDateTime.of(2026, 1, 1, 12, 0);
        for (int i = 0; i < 27; i++) {
            long targetUser = i < 25 ? userId : owner + (i == 25 ? 10 : 11);
            long targetApp = i == 25 ? otherApp : app;
            jdbc.update("INSERT INTO portal_payment_order(order_no,app_user_id,app_id,amount_fen,created_at) VALUES (?,?,?,100,?)",
                    fixture + i, targetUser, targetApp, sameSecond);
        }
        var expected = jdbc.queryForList("SELECT id FROM portal_payment_order WHERE app_user_id=? ORDER BY id DESC", Long.class, userId);
        var first = get("/api/portal/payment/orders?page=1&size=20", token);
        var second = get("/api/portal/payment/orders?page=2&size=20", token);
        assertCode(200, first); assertCode(200, second);
        List<Long> pageOne = orderIds(first), pageTwo = orderIds(second);
        assertEquals(expected.subList(0, 20), pageOne);
        assertEquals(expected.subList(20, 25), pageTwo);
        assertTrue(Collections.disjoint(pageOne, pageTwo));
        assertEquals(pageOne, orderIds(get("/api/portal/payment/orders?page=1&size=20", token)));
        for (var response : List.of(first, second)) for (var order : response.path("data")) {
            assertEquals(app, order.path("appId").asLong());
            assertEquals(userId.longValue(), order.path("appUserId").asLong());
        }
    }
    List<Long> orderIds(JsonNode response) {
        List<Long> ids = new ArrayList<>();
        response.path("data").forEach(order -> ids.add(order.path("id").asLong()));
        return ids;
    }

    @Test void updateDownloadAuthenticatesTicketAndChargesExactBytesOnlyForAcceptedTransfer() throws Exception {
        byte[] content = "CipherGate acceptance package\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String objectKey = "updates/" + fixture + "/application-update.apk";
        jdbc.update("UPDATE application SET update_file_storage_key=?,traffic_limit=?,traffic_used=0 WHERE id=?", objectKey, content.length, app);
        jdbc.update("UPDATE user_membership SET extra_traffic_quota=?,traffic_used=0 WHERE user_id=?", content.length * 2L, owner);
        when(objects.contentLengthDefaultBucket(objectKey)).thenReturn((long) content.length);
        var streams = new ArrayList<java.io.ByteArrayInputStream>();
        when(objects.download(minio.getBucket(), objectKey)).thenAnswer(call -> {
            var stream = spy(new java.io.ByteArrayInputStream(content));
            streams.add(stream); return stream;
        });

        var rejected = download(tickets.mint(app, "incorrect-secret"));
        assertEquals(403, rejected.statusCode());
        assertEquals(0, rejected.body().length);
        assertTrafficUsed(0);
        verifyNoInteractions(objects);

        String ticket = tickets.mint(app, secret);
        var accepted = download(ticket);
        assertEquals(200, accepted.statusCode());
        assertArrayEquals(content, accepted.body());
        assertEquals(Integer.toString(content.length), accepted.headers().firstValue("Content-Length").orElseThrow());
        assertEquals("application/octet-stream", accepted.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("attachment; filename=\"application-update.apk\"", accepted.headers().firstValue("Content-Disposition").orElseThrow());
        assertFalse(accepted.headers().firstValue("Content-Disposition").orElseThrow().contains(fixture));
        assertTrafficUsed(content.length);

        var exhausted = download(ticket);
        assertEquals(403, exhausted.statusCode());
        assertEquals(0, exhausted.body().length);
        assertTrafficUsed(content.length);
        assertEquals(2, streams.size());
        for (var stream : streams) verify(stream, atLeastOnce()).close();
    }

    HttpResponse<byte[]> download(String ticket) throws Exception {
        var request = HttpRequest.newBuilder(uri("/api/v1/app/update-package?ticket=" + ticket))
                .timeout(java.time.Duration.ofSeconds(15)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
    void assertTrafficUsed(long bytes) {
        assertEquals(bytes, jdbc.queryForObject("SELECT traffic_used FROM application WHERE id=?", Long.class, app));
        assertEquals(bytes, jdbc.queryForObject("SELECT traffic_used FROM user_membership WHERE user_id=?", Long.class, owner));
    }

    String requestRegistrationCode() throws Exception {
        var challenge = get("/api/public/challenge?purpose=register-email", null);
        assertCode(200, challenge);
        String id = challenge.path("data").path("captchaId").asText();
        assertTrue(challenge.path("data").path("image").asText().startsWith("data:image/png;base64,"));
        String key = "public:challenge:" + id; redisKeys.add(key);
        String answer = Objects.requireNonNull(redis.opsForValue().get(key));
        assertCode(200, post("/api/public/app-user/register/send-email-code", Map.of("appId", app, "email", email, "captchaId", id, "captchaCode", answer.substring(answer.lastIndexOf(':') + 1))));
        assertNotNull(deliveredCode);
        verify(mail).sendPlainText(eq(email), anyString(), anyString());
        return deliveredCode;
    }
    Map<String, Object> registration(String code) {
        return Map.of("appId", app, "email", email, "username", username, "emailCode", code, "password", PASSWORD);
    }
    JsonNode register(String code) throws Exception { return post("/api/public/app-user/register/submit", registration(code)); }
    JsonNode login(String password, boolean badCaptcha) throws Exception {
        var challenge = get("/api/portal/auth/captcha", null);
        assertCode(200, challenge);
        String id = challenge.path("data").path("captchaId").asText();
        String key = "portal:captcha:" + id; redisKeys.add(key);
        String answer = Objects.requireNonNull(redis.opsForValue().get(key));
        return post("/api/portal/auth/login", Map.of("email", email, "password", password, "captchaId", id, "captchaCode", badCaptcha ? "invalid" : answer));
    }
    ThirdPartyIntegrationRequest envelope(ThirdPartyIntegrationService.Action action) {
        var request = new ThirdPartyIntegrationRequest();
        request.setAppId(app); request.setApiKey(apiKey); request.setUserEmail(email);
        request.setTimestamp(System.currentTimeMillis()); request.setNonce(UUID.randomUUID().toString().replace("-", ""));
        request.setSign(ThirdPartySignatureVerifier.hmacSha256Hex(secret, ThirdPartyIntegrationService.signingText(action, request)));
        return request;
    }
    JsonNode integration(ThirdPartyIntegrationService.Action action) throws Exception {
        return post("/api/v1/third_party/" + (action == ThirdPartyIntegrationService.Action.TRIAL_APPLY ? "trial" : "authorization"), envelope(action));
    }
    String insertCard(long targetApp) {
        String card = "ACCEPT-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO license_key(app_id,owner_id,key_code,key_type,duration_value,duration_unit) VALUES (?,?,?,'DAY',7,'DAY')", targetApp, owner, card);
        return card;
    }
    JsonNode redeem(String card) throws Exception {
        return post("/api/public/app-user/self/redeem-license", Map.of("appId", app, "email", email, "keyCode", card));
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE app_id=?", Integer.class, app); }
    String lastError() { return jdbc.queryForObject("SELECT error_code FROM third_party_recharge_log WHERE credential_id=? ORDER BY id DESC LIMIT 1", String.class, credential); }
    JsonNode post(String path, Object body) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).timeout(java.time.Duration.ofSeconds(15)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build());
    }
    JsonNode get(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).timeout(java.time.Duration.ofSeconds(15)).GET();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return send(builder.build());
    }
    URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    JsonNode send(HttpRequest request) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"), () -> "Expected JSON HTTP response for " + request.uri().getPath());
        JsonNode result = json.readTree(response.body());
        if (request.uri().getPath().startsWith("/api/v1/third_party/")) assertEquals(response.statusCode(), result.path("code").asInt());
        return result;
    }
    void assertCode(int expected, JsonNode body) { assertEquals(expected, body.path("code").asInt(), () -> "Unexpected business response: " + body.path("message").asText()); }
}
