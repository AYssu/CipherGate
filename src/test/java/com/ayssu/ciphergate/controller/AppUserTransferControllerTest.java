package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.aspect.PermissionAspect;
import com.ayssu.ciphergate.dto.transfer.AppUserTransferRequest;
import com.ayssu.ciphergate.dto.transfer.AppUserTransferResult;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.handler.GlobalExceptionHandler;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.AppUserTransferService;
import com.ayssu.ciphergate.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 MVC JSON/校验/HTTP 状态与权限切面；业务事务另由 MySQL 测试验证。 */
class AppUserTransferControllerTest {
    private AppUserTransferService service;
    private UserService permissions;
    private UserMapper users;
    private MockMvc mvc;
    private User operator;
    private static final String URL = "/api/app-user-transfers";
    private static final String BODY = """
            {"sourceAppId":10,"targetAppId":20,"mode":"COPY","forceOverwrite":false,"userIds":[100]}
            """;

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        service = mock(AppUserTransferService.class); permissions = mock(UserService.class); users = mock(UserMapper.class);
        operator = new User(); operator.setId(1L);
        when(permissions.hasPermission(1L, "APP_USER_TRANSFER")).thenReturn(true);
        var aspect = new PermissionAspect(); ReflectionTestUtils.setField(aspect, "userService", permissions);
        var factory = new AspectJProxyFactory(new AppUserTransferController(service, users));
        factory.setProxyTargetClass(true); factory.addAspect(aspect);
        AppUserTransferController proxy = factory.getProxy();
        mvc = MockMvcBuilders.standaloneSetup(proxy).setControllerAdvice(new GlobalExceptionHandler()).build();
        login();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private void login() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
    @Test void sourceUserFiltersBindIncludingCommaSeparatedSelectedIds() throws Exception {
        when(service.sourceUsers(eq(10L), eq(1L), eq(2L), eq(20L), eq("alpha"), any())).thenReturn(new Page<>(2, 20, 0));
        mvc.perform(get(URL + "/users").param("appId", "10").param("current", "2").param("keyword", "alpha")
                        .param("username", "alpha").param("nickname", "Profile").param("email", "example.test")
                        .param("phone", "138").param("memberStatus", "ACTIVE").param("userIds", "100,101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(service).sourceUsers(eq(10L), eq(1L), eq(2L), eq(20L), eq("alpha"), argThat(f ->
                f.getUsername().equals("alpha") && f.getNickname().equals("Profile") && f.getEmail().equals("example.test")
                && f.getPhone().equals("138") && f.getMemberStatus().equals("ACTIVE") && f.getUserIds().equals(List.of(100L, 101L))));
    }
    @Test void malformedSelectedIdsReturn400WithoutCallingService() throws Exception {
        mvc.perform(get(URL + "/users").param("appId", "10").param("userIds", "not-a-number")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void sourceFiltersRequireTransferPermission() throws Exception {
        when(permissions.hasPermission(1L, "APP_USER_TRANSFER")).thenReturn(false);
        mvc.perform(get(URL + "/users").param("appId", "10").param("email", "example"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void previewReturnsSafeJsonAndUsesCurrentOperator() throws Exception {
        when(service.preview(any(), eq(1L))).thenReturn(new AppUserTransferResult("Source", "Target", "test_preview", 1, 0, 0, 0, 0, false, List.of()));
        mvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.createCount").value(1)).andExpect(jsonPath("$.data.password").doesNotExist());
        verify(service).preview(argThat(r -> r.getMode() == AppUserTransferRequest.Mode.COPY && r.getUserIds().equals(List.of(100L))), eq(1L));
    }
    @Test void unauthenticatedRequestIs401AndNeverCallsService() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void missingPermissionIs403AndNeverCallsService() throws Exception {
        when(permissions.hasPermission(1L, "APP_USER_TRANSFER")).thenReturn(false);
        mvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void appOwnershipDenialIs403NotSuccessfulBusinessResponse() throws Exception {
        when(service.preview(any(), anyLong())).thenThrow(new SecurityException("仅应用所有者或管理员可操作"));
        mvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    }
    @Test void passwordSessionAuthenticationIsRestored() throws Exception {
        var auth = SecurityContextHolder.getContext().getAuthentication(); SecurityContextHolder.clearContext();
        var session = new MockHttpSession(); session.setAttribute("passwordAuth", auth);
        when(service.applicationOptions(1L, 1, 50, null)).thenReturn(new Page<>());
        mvc.perform(get(URL + "/applications").session(session)).andExpect(status().isOk());
        verify(service).applicationOptions(1L, 1, 50, null);
    }
    @Test void githubOAuthUserResolvesToSameLocalOperator() throws Exception {
        var principal = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("id", "test-github"), "id");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(permissions.getUserByGithubId("test-github")).thenReturn(operator);
        when(users.selectOne(any())).thenReturn(operator);
        mvc.perform(get(URL + "/applications")).andExpect(status().isOk());
        verify(service).applicationOptions(1L, 1, 50, null);
    }
    @Test void invalidUserIdsAre400BeforeService() throws Exception {
        mvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON).content(BODY.replace("[100]", "[0,null]")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void malformedJsonAndInvalidModeAre400() throws Exception {
        mvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON).content(BODY.replace("COPY", "INVALID")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void missingAppParameterAndWrongTypeAre400() throws Exception {
        mvc.perform(get(URL + "/users")).andExpect(status().isBadRequest());
        mvc.perform(get(URL + "/users").param("appId", "not-a-number")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void serviceValidationIs400WithUsefulMessage() throws Exception {
        when(service.execute(any(), eq(1L))).thenThrow(new IllegalArgumentException("请先预检查后再执行"));
        mvc.perform(post(URL + "/execute").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("请先预检查后再执行"));
    }
    @Test void databaseErrorsDoNotExposeSecretsOrSql() throws Exception {
        when(service.preview(any(), anyLong())).thenThrow(new IllegalStateException("SQL test_only_secret"));
        mvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("服务器内部错误"));
    }
}
