package com.ayssu.ciphergate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ayssu.ciphergate.portal.util.PortalJwtUtil;
import org.springframework.boot.info.BuildProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
class CipherGateApplicationTests {
    @LocalServerPort int port;
    @Autowired PortalJwtUtil jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired BuildProperties build;
    @Autowired com.ayssu.ciphergate.mapper.AppUserMapper appUsers;

    @BeforeAll static void requireIsolatedDatabase() {
        String url = System.getenv("CIPHERGATE_BOOT_JDBC_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/ciphergate_boot_test(?:\\?.*)?")) {
            throw new IllegalStateException("CIPHERGATE_BOOT_JDBC_URL must use ciphergate_boot_test");
        }
    }

    @Test
    void contextLoads() {
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class) > 0);
    }

    @Test void unauthenticatedPrivateEndpointsRejectRequests() throws Exception {
        assertEquals(401, request("/api/user/info", null).statusCode());
        assertEquals(401, request("/api/portal/settings/profile", null).statusCode());
        assertEquals(401, request("/api/portal/admin/orders", null).statusCode());
    }

    @Test void portalTokenCannotCrossIntoAdministratorApi() throws Exception {
        String token = jwt.generateToken(999999L, 999999L, "test@example.test", "Test");
        for (String path : new String[]{"/api/user/info", "/api/applications", "/api/portal/admin/orders",
                "/api/portal/epay-config/1", "/actuator/info"}) {
            assertEquals(401, request(path, token).statusCode(), path);
        }
    }

    @Test void portalTokenAuthenticatesOwnProfileOnRealSecurityFilterChain() throws Exception {
        jdbc.update("INSERT INTO users (id,github_id,login) VALUES (999999,'ci-security-owner','ci-security-owner') ON DUPLICATE KEY UPDATE login=VALUES(login)");
        jdbc.update("INSERT INTO application(id,owner_id,app_name,app_key,app_secret,status) VALUES (999999,999999,'CI Security','ci-security-key','test-only',1) ON DUPLICATE KEY UPDATE status=1");
        jdbc.update("INSERT INTO app_user (app_id, username, nickname, email) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), password=NULL",
                999999L, "ci-security-user", "CI Member", "ci-security@example.test");
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE app_id=? AND username=?",
                Long.class, 999999L, "ci-security-user");
        var response = request("/api/portal/settings/profile",
                jwt.generateSessionToken(appUsers.selectById(userId)));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("CI Member"));
        assertFalse(response.body().contains("password"));
        var user = appUsers.selectById(userId);
        String session = jwt.generateSessionToken(user);
        String selection = jwt.generateSelectionToken(user.getEmail(), java.util.List.of(user));
        assertEquals(403, request("/api/portal/settings/profile", selection).statusCode());
        jdbc.update("UPDATE app_user SET password='changed-credential' WHERE id=?", userId);
        assertEquals(401, request("/api/portal/settings/profile", session).statusCode());
        assertEquals(200, request("/api/portal/settings/profile", jwt.generateSessionToken(appUsers.selectById(userId))).statusCode());
        jdbc.update("UPDATE application SET status=0 WHERE id=999999");
        assertEquals(401, request("/api/portal/settings/profile", jwt.generateSessionToken(appUsers.selectById(userId))).statusCode());
        jdbc.update("UPDATE application SET status=1 WHERE id=999999");
    }

    @Test void publicConfigurationAndHealthProbesWorkWithoutCredentials() throws Exception {
        assertEquals(200, request("/api/config/init/status", null).statusCode());
        var liveness = request("/actuator/health/liveness", null);
        assertEquals(200, liveness.statusCode());
        assertTrue(liveness.body().contains("UP"));
        assertEquals(200, request("/actuator/health/readiness", null).statusCode());
        assertEquals(401, request("/actuator/info", null).statusCode());
    }

    @Test void publicBuildInfoMatchesRunningBackendWithoutExposingOtherProperties() throws Exception {
        var response = request("/api/config/public/build-info", null);
        assertEquals(200, response.statusCode());
        assertEquals("{\"version\":\"" + build.getVersion() + "\"}", response.body());
    }

    private HttpResponse<String> request(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

}
