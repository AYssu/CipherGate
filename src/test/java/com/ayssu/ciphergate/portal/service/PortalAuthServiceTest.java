package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.portal.dto.*;
import com.ayssu.ciphergate.portal.mapper.*;
import com.ayssu.ciphergate.portal.util.PortalJwtUtil;
import com.ayssu.ciphergate.service.*;
import com.ayssu.ciphergate.service.mail.SystemSmtpMailService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalAuthServiceTest {
    AppUserMapper users = mock(AppUserMapper.class); ApplicationMapper apps = mock(ApplicationMapper.class);
    PortalLoginLogMapper logs = mock(PortalLoginLogMapper.class); PortalVerifyCodeMapper codes = mock(PortalVerifyCodeMapper.class);
    PortalJwtUtil jwt = new PortalJwtUtil("portal-unit-testing-secret-long-enough-123456789", 3600000);
    StringRedisTemplate redis = mock(StringRedisTemplate.class); ValueOperations<String,String> values = mock(ValueOperations.class);
    PublicAbuseGuard guard = mock(PublicAbuseGuard.class); PortalOtpService otp = mock(PortalOtpService.class);
    PortalRecoveryDispatcher dispatcher = mock(PortalRecoveryDispatcher.class);
    PortalAuthService auth = new PortalAuthService(users, apps, logs, codes, jwt, redis, mock(GeoIpService.class), mock(SystemSmtpMailService.class), guard, otp, dispatcher);
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    @BeforeEach void setup() {
        for (Class<?> c : List.of(AppUser.class,Application.class)) TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), c);
        when(redis.opsForValue()).thenReturn(values); when(values.getAndDelete("portal:captcha:captcha")).thenReturn("1234");
        when(apps.selectBatchIds(anyCollection())).thenReturn(List.of());
        for(long id:List.of(1L,2L)) { Application app=new Application(); app.setId(id); app.setStatus(1); when(apps.selectById(id)).thenReturn(app); }
    }
    AppUser user(long app, String password) { AppUser user=new AppUser(); user.setId(app+10); user.setAppId(app); user.setEmail("u@example.test");user.setPassword(encoder.encode(password)); return user; }
    PortalLoginRequest request() { var req=new PortalLoginRequest(); req.setEmail("u@example.test");req.setPassword("first-password");req.setCaptchaId("captcha");req.setCaptchaCode("1234");return req; }
    @Test void onePasswordNeverAuthorizesSiblingApplication() {
        var first=user(1,"first-password");var second=user(2,"second-password");
        when(users.selectList(any())).thenReturn(List.of(first,second));
        var result=auth.login(request(),"192.0.2.1");
        assertFalse(result.isNeedSelectApp()); assertEquals(1,result.getApps().size()); assertEquals(1L,jwt.getAppIdFromToken(result.getToken()));
        when(users.selectOne(any())).thenReturn(second);
        assertThrows(IllegalArgumentException.class,()->auth.selectApp(2L,second.getEmail(), Map.of(first.getId().toString(),jwt.credentialStamp(first)),"192.0.2.1"));
    }
    @Test void selectionTicketContainsOnlyVerifiedAccountsAndExpiresInFiveMinutes() {
        var first=user(1,"first-password");var second=user(2,"first-password");when(users.selectList(any())).thenReturn(List.of(first,second));
        var result=auth.login(request(),"192.0.2.1"); var claims=jwt.parseToken(result.getToken());
        assertTrue(result.isNeedSelectApp()); assertEquals("SELECT_APP",claims.get("purpose"));
        assertEquals(300000,claims.getExpiration().getTime()-claims.getIssuedAt().getTime());
        Map<String,?> grants=(Map<String,?>)claims.get("grants");
        when(users.selectOne(any())).thenReturn(second);
        assertEquals(2L,jwt.getAppIdFromToken(auth.selectApp(2L,second.getEmail(),grants,"192.0.2.1")));
        second.setPassword(encoder.encode("changed-password"));
        assertThrows(IllegalArgumentException.class,()->auth.selectApp(2L,second.getEmail(),grants,"192.0.2.1"));
    }
    @Test void recoveryDiscoveryRequiresCodeBeforeAnyUserQuery() {
        doThrow(new IllegalArgumentException("invalid")).when(otp).verify("u@example.test","RECOVERY","wrong",false);
        assertThrows(IllegalArgumentException.class,()->auth.getAppsByEmail("u@example.test","wrong","192.0.2.1"));
        verifyNoInteractions(users);
    }
    @Test void absentRecoveryAccountHasSameSuccessfulSendOutcomeWithoutSendingMail() {
        auth.sendRecoveryCode("u@example.test","192.0.2.1","captcha","1234");
        verify(otp).claimSend("u@example.test","RECOVERY"); verify(dispatcher).dispatch("u@example.test"); verifyNoInteractions(users);
    }
    @Test void normalizationSharesLoginBudgetAndMalformedHashesFailClosed() {
        var req=request(); req.setEmail(" U@Example.Test ");
        var legacy=user(1,"irrelevant");legacy.setPassword("not-bcrypt");when(users.selectList(any())).thenReturn(List.of(legacy));
        assertThrows(IllegalArgumentException.class,()->auth.login(req,"192.0.2.1"));
        verify(guard).check("portal-login-account","u@example.test",20,java.time.Duration.ofMinutes(15));
        assertEquals("u@example.test",req.getEmail());
    }

    @Test void invalidCaptchaCannotReachPasswordLookup() {
        when(values.getAndDelete(anyString())).thenReturn(null); assertThrows(IllegalArgumentException.class,()->auth.login(request(),"192.0.2.1"));verifyNoInteractions(users);
    }
    @Test void resetValidatesPasswordBeforeConsumingCode() {
        var r=new PortalPasswordRecoveryRequest();r.setNewPassword("短");
        assertThrows(IllegalArgumentException.class,()->auth.resetPassword(r,"192.0.2.1")); verifyNoInteractions(otp,users);
    }
}
