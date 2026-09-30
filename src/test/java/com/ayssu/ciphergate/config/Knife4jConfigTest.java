package com.ayssu.ciphergate.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Knife4jConfigTest {

    private Knife4jConfig config;
    private OpenAPI openApi;

    @BeforeEach
    void setUp() {
        config = new Knife4jConfig();
        openApi = config.customOpenAPI();
        openApi.setPaths(new Paths()
                .addPathItem("/api/users", getOperationPathItem())
                .addPathItem("/api/auth/login", postOperationPathItem())
                .addPathItem("/api/portal/dashboard/stats", getOperationPathItem())
                .addPathItem("/api/portal/auth/login", postOperationPathItem())
                .addPathItem("/api/v1/card/login", postOperationPathItem())
                .addPathItem("/api/v1/app/update-package", getOperationPathItem())
                .addPathItem("/api/v1/third_party/recharge", postOperationPathItem())
                .addPathItem("/api/payment/notify", postOperationPathItem()));
    }

    @Test
    void documentsProductionAndLocalServers() {
        assertEquals("CipherGate API 文档", openApi.getInfo().getTitle());
        assertTrue(openApi.getInfo().getDescription().contains("用户门户"));
        assertTrue(openApi.getServers().stream().anyMatch(server ->
                "https://www.ayssu.com".equals(server.getUrl())));
        assertTrue(openApi.getServers().stream().anyMatch(server ->
                "http://localhost:8080".equals(server.getUrl())));
    }

    @Test
    void declaresAllSupportedAuthenticationSchemes() {
        Map<String, ?> schemes = openApi.getComponents().getSecuritySchemes();

        assertTrue(schemes.keySet().containsAll(List.of(
                "sessionCookie", "portalBearer", "xAppKey", "xTimestamp",
                "xNonce", "xSignature", "updateTicket"
        )));
        assertEquals(7, schemes.size());
    }

    @Test
    void appliesAuthenticationOnlyToProtectedPaths() {
        config.apiSecurityCustomizer().customise(openApi);

        assertEquals(List.of("sessionCookie"), schemeNames("/api/users"));
        assertTrue(schemeNames("/api/portal/dashboard/stats").contains("portalBearer"));
        assertEquals(
                List.of("xAppKey", "xTimestamp", "xNonce", "xSignature"),
                schemeNames("/api/v1/card/login")
        );
        assertEquals(List.of("updateTicket"), schemeNames("/api/v1/app/update-package"));

        assertTrue(openApi.getPaths().get("/api/users").getGet().getResponses().containsKey("401"));
        assertTrue(openApi.getPaths().get("/api/users").getGet().getResponses().containsKey("403"));
    }

    @Test
    void leavesLoginPublicPortalAndCallbacksUnprotected() {
        config.apiSecurityCustomizer().customise(openApi);

        assertNull(openApi.getPaths().get("/api/auth/login").getPost().getSecurity());
        assertNull(openApi.getPaths().get("/api/portal/auth/login").getPost().getSecurity());
        assertNull(openApi.getPaths().get("/api/payment/notify").getPost().getSecurity());
        assertNull(openApi.getPaths().get("/api/v1/third_party/recharge").getPost().getSecurity());
    }

    @Test
    void protectsSensitiveInitializationReset() {
        openApi.getPaths().addPathItem("/api/config/init/reset", postOperationPathItem());
        config.apiSecurityCustomizer().customise(openApi);

        assertEquals(List.of("sessionCookie"), schemeNames("/api/config/init/reset"));
    }

    @Test
    void exposesCompleteFunctionalGroups() {
        assertEquals(18, groupNames().size());
        assertTrue(groupNames().containsAll(List.of(
                "01. 认证与个人中心",
                "05. 消息、公告与文档",
                "11. 支付与工单",
                "12. 用户门户",
                "16. 第三方协议",
                "98. 运维与调试",
                "99. 其他接口"
        )));
    }

    private List<String> schemeNames(String path) {
        Operation operation = operationAt(path);
        assertNotNull(operation.getSecurity());
        assertFalse(operation.getSecurity().isEmpty());

        return operation.getSecurity().get(0).keySet().stream().toList();
    }

    private Operation operationAt(String path) {
        PathItem pathItem = openApi.getPaths().get(path);
        Operation operation = pathItem.getGet() != null
                ? pathItem.getGet()
                : pathItem.getPost();
        assertNotNull(operation);
        return operation;
    }

    private List<String> groupNames() {
        return List.of(
                config.authApi(), config.userApi(), config.systemApi(), config.activityApi(),
                config.messageApi(), config.applicationApi(), config.appVariableApi(),
                config.appUserApi(), config.pluginApi(), config.membershipApi(),
                config.paymentApi(), config.portalApi(), config.thirdPartyCredentialApi(),
                config.publicApi(), config.uploadApi(), config.thirdPartyApi(),
                config.opsApi(), config.otherApi()
        ).stream().map(group -> group.getGroup()).toList();
    }

    private PathItem getOperationPathItem() {
        return new PathItem().get(operation("example-get"));
    }

    private PathItem postOperationPathItem() {
        return new PathItem().post(operation("example-post"));
    }

    private Operation operation(String id) {
        return new Operation()
                .operationId(id)
                .responses(new ApiResponses());
    }
}
