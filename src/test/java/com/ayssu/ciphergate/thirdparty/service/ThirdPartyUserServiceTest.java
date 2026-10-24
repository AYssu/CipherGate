package com.ayssu.ciphergate.thirdparty.service;

import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.service.AccessEventService;
import com.ayssu.ciphergate.thirdparty.dto.UserLoginRequest;
import com.ayssu.ciphergate.thirdparty.ws.service.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class ThirdPartyUserServiceTest {
    private ApplicationMapper apps;
    private AppUserMapper users;
    private AppUserBindingMapper bindings;
    private AppUserWsAuthService auth;
    private AppUserWsDeviceBindService devices;
    private AppUserWsLoginRecorder recorder;
    private ThirdPartyAppVariableService variables;
    private AccessEventMapper events;
    private ThirdPartyUserService service;
    private Application app;
    private AppUser user;
    private UserLoginRequest request;

    @BeforeEach
    void setUp() {
        for (Class<?> type : List.of(AppUser.class, AppUserBinding.class, Application.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), type);
        }
        apps = mock(ApplicationMapper.class);
        users = mock(AppUserMapper.class);
        bindings = mock(AppUserBindingMapper.class);
        auth = mock(AppUserWsAuthService.class);
        devices = mock(AppUserWsDeviceBindService.class);
        recorder = mock(AppUserWsLoginRecorder.class);
        variables = mock(ThirdPartyAppVariableService.class);
        events = mock(AccessEventMapper.class);
        service = new ThirdPartyUserService(apps, users, bindings, auth, devices, recorder,
                variables, new AccessEventService(events));
        app = new Application(); app.setId(1L); app.setStatus(1); app.setBusinessModel(1);
        app.setAppKey("test-app");
        user = new AppUser(); user.setId(7L); user.setAppId(1L); user.setUsername("alice");
        user.setNickname("Alice"); user.setLoginCount(3); user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp("192.0.2.1"); user.setMemberExpiresAt(LocalDateTime.now().plusDays(1));
        request = new UserLoginRequest(); request.setUsername("alice");
        request.setPassword("test & = 密码 "); request.setDeviceId(" device-1 ");
        when(apps.selectById(1L)).thenReturn(app);
        when(auth.loginAppUser(eq(1L), anyString(), anyString())).thenReturn(user);
        when(users.selectById(7L)).thenReturn(user);
        when(bindings.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(variables.getEnabledVariablesMap(eq(1L), any())).thenReturn(Map.of("hello", "Alice"));
    }

    @Test
    void paidLoginReturnsUserFieldsAndRecordsHttpIdentityWithoutWsOrToken() {
        var response = service.login(1L, request, "192.0.2.1");
        assertEquals(7L, response.getUserId()); assertEquals("alice", response.getUsername());
        assertEquals(1L, response.getBindingCount()); assertEquals(3, response.getLoginCount());
        assertTrue(response.getMemberRequired()); assertTrue(response.getAvailable() > 86000);
        assertEquals(user.getMemberExpiresAt(), response.getExpiresAt());
        assertEquals("{\"hello\":\"Alice\"}", response.getVariables());
        assertTrue(response.getOnline()); assertNull(response.getToken());
        assertEquals("USER", response.getIdentityType()); assertEquals("user_7", response.getIdentityId());
        verify(auth).loginAppUser(1L, "alice", "test & = 密码 ");
        verify(devices).bindOrTouchOnWsLogin(1L, 7L, "device-1", "", "", "192.0.2.1");
        verify(recorder).recordSuccessfulHttpLogin(1L, 7L, "192.0.2.1", "device-1");
        verify(recorder, never()).recordSuccessfulLogin(any(), any(), any(), any());
        var event = ArgumentCaptor.forClass(AccessEvent.class);
        verify(events).insert(event.capture());
        assertEquals("APP_USER_HTTP_LOGIN", event.getValue().getEventType());
        assertEquals(7L, event.getValue().getRefId());
        assertEquals("192.0.2.1", event.getValue().getClientIp());
        assertTrue(event.getValue().getDeviceHash().matches("[a-f0-9]{64}"));
        var context = ArgumentCaptor.forClass(AppVariableTemplateContext.class);
        verify(variables).getEnabledVariablesMap(eq(1L), context.capture());
        assertEquals(7L, context.getValue().getUserId());
        assertEquals(3, context.getValue().getUserLoginCount());
        assertNull(context.getValue().getWsConnId());
    }

    @Test
    void freeAppStillAuthenticatesButDoesNotRequireOrInventMembership() {
        app.setBusinessModel(2); user.setMemberExpiresAt(null);
        var response = service.login(1L, request, "192.0.2.1");
        assertFalse(response.getMemberRequired()); assertNull(response.getExpiresAt());
        assertTrue(response.getOnline()); assertNull(response.getToken());
        assertNull(response.getAvailable()); assertEquals("USER", response.getIdentityType());
        verify(auth).loginAppUser(1L, "alice", request.getPassword());
    }

    @Test
    void expiredAndMissingMembershipDeniedBeforeSideEffects() {
        for (LocalDateTime expiry : new LocalDateTime[]{null, LocalDateTime.now().minusSeconds(1)}) {
            user.setMemberExpiresAt(expiry);
            var error = assertThrows(ThirdPartyUserLoginException.class, () -> service.login(1L, request, null));
            assertEquals(403, error.getCode()); assertEquals("MEMBER_EXPIRED", error.getMessage());
        }
        verifyNoInteractions(devices, recorder, variables, events);
    }

    @Test
    void membershipRevokedDuringLoginDoesNotProduceSuccess() {
        AppUser refreshed = new AppUser();
        refreshed.setId(7L); refreshed.setAppId(1L); refreshed.setMemberExpiresAt(null);
        when(users.selectById(7L)).thenReturn(refreshed);
        assertEquals("MEMBER_EXPIRED", assertThrows(ThirdPartyUserLoginException.class,
                () -> service.login(1L, request, null)).getMessage());
        verifyNoInteractions(variables, events);
    }

    @Test
    void disabledOrDeletedApplicationDeniedEvenIfFilterWasBypassed() {
        app.setStatus(0);
        assertEquals(403, assertThrows(ThirdPartyUserLoginException.class,
                () -> service.login(1L, request, null)).getCode());
        app.setStatus(1); app.setDeleted(1);
        assertThrows(ThirdPartyUserLoginException.class, () -> service.login(1L, request, null));
        assertThrows(ThirdPartyUserLoginException.class, () -> service.login(2L, request, null));
        verifyNoInteractions(auth, devices, recorder, events);
    }

    @Test
    void wrongPasswordAndCrossAppUserDoNotLeakDetailsOrRecordSuccess() {
        when(auth.loginAppUser(anyLong(), anyString(), anyString())).thenThrow(new IllegalArgumentException("bad credentials"));
        assertEquals("用户名或密码错误", assertThrows(ThirdPartyUserLoginException.class,
                () -> service.login(1L, request, null)).getMessage());
        doReturn(user).when(auth).loginAppUser(anyLong(), anyString(), anyString());
        user.setAppId(2L);
        assertEquals(401, assertThrows(ThirdPartyUserLoginException.class,
                () -> service.login(1L, request, null)).getCode());
        verifyNoInteractions(devices, recorder, events);
    }

    @Test
    void bannedAndConflictingDevicesCannotLogin() {
        for (String reason : List.of("DEVICE_BANNED", "DEVICE_CONFLICT")) {
            doThrow(new IllegalStateException(reason)).when(devices)
                    .bindOrTouchOnWsLogin(any(), any(), any(), any(), any(), any());
            assertEquals(reason, assertThrows(ThirdPartyUserLoginException.class,
                    () -> service.login(1L, request, null)).getMessage());
        }
        verifyNoInteractions(recorder, events, variables);
    }

    @Test
    void invalidParametersRejectedBeforeAuthentication() {
        assertThrows(ThirdPartyUserLoginException.class, () -> service.login(1L, null, null));
        request.setDeviceId(" ");
        assertEquals(400, assertThrows(ThirdPartyUserLoginException.class,
                () -> service.login(1L, request, null)).getCode());
        request.setDeviceId("x".repeat(256));
        assertThrows(ThirdPartyUserLoginException.class, () -> service.login(1L, request, null));
        request.setDeviceId("valid"); request.setDeviceName("x".repeat(101));
        assertThrows(ThirdPartyUserLoginException.class, () -> service.login(1L, request, null));
        verifyNoInteractions(apps, auth, events);
    }

    @Test
    void variableFailureDoesNotEmitSuccessAndTransactionRollsBack() throws Exception {
        when(variables.getEnabledVariablesMap(anyLong(), any())).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> service.login(1L, request, null));
        verifyNoInteractions(events);
        var tx = new AnnotationTransactionAttributeSource().getTransactionAttribute(
                ThirdPartyUserService.class.getMethod("login", Long.class, UserLoginRequest.class, String.class),
                ThirdPartyUserService.class);
        assertNotNull(tx); assertTrue(tx.rollbackOn(new Exception("variable failure")));
    }

    @Test
    void passwordNeverAppearsInRequestToString() {
        assertFalse(request.toString().contains(request.getPassword()));
    }
}
