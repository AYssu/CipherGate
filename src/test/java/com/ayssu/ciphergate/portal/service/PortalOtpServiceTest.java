package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.portal.entity.PortalVerifyCode;
import com.ayssu.ciphergate.portal.mapper.PortalVerifyCodeMapper;
import com.ayssu.ciphergate.service.PublicAbuseGuard;
import com.ayssu.ciphergate.service.mail.SystemSmtpMailService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.mockito.ArgumentCaptor;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PortalOtpServiceTest {
    PortalVerifyCodeMapper codes=mock(PortalVerifyCodeMapper.class);StringRedisTemplate redis=mock(StringRedisTemplate.class);
    PublicAbuseGuard guard=mock(PublicAbuseGuard.class);SystemSmtpMailService mail=mock(SystemSmtpMailService.class);
    PortalOtpService otp=new PortalOtpService(codes,redis,guard,mail);
    @BeforeEach void setup() {TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"test"),PortalVerifyCode.class);}
    @Test void invalidCodeCannotConsumeAndAttemptLimitPersistsOutsideDatabase() {
        PortalVerifyCode row=new PortalVerifyCode();row.setId(1L);row.setCode("123456");row.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(codes.selectOne(any())).thenReturn(row);
        assertThrows(IllegalArgumentException.class,()->otp.verify("u@example.test","RECOVERY","wrong",true));
        verify(guard).check("portal-otp-attempt","1",8,Duration.ofMinutes(10));verify(codes,never()).update(isNull(),any());
    }
    @Test void correctCodeMustWinConditionalConsumption() {
        PortalVerifyCode row=new PortalVerifyCode();row.setId(1L);row.setCode("123456");row.setExpiresAt(LocalDateTime.now().plusMinutes(5));when(codes.selectOne(any())).thenReturn(row);
        when(codes.update(isNull(),any())).thenReturn(0);
        assertThrows(IllegalArgumentException.class,()->otp.verify("u@example.test","RECOVERY","123456",true));
    }
    @Test void sendingUsesBoundPurposeAndNeverLogsRawCode() {
        when(mail.isMailEnabledAndConfigured()).thenReturn(true);
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(PortalOtpService.class);
        var capture=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();capture.start();logger.addAppender(capture);
        try {
            otp.send("u@example.test","EMAIL_CHANGE:1:2");
            var row=ArgumentCaptor.forClass(PortalVerifyCode.class);verify(codes).insert(row.capture());
            assertTrue(row.getValue().getCode().matches("[0-9]{6}"));assertEquals("EMAIL_CHANGE:1:2",row.getValue().getPurpose());
            assertTrue(capture.list.stream().noneMatch(event->event.getFormattedMessage().contains(row.getValue().getCode())));
        } finally {logger.detachAppender(capture);}
    }
    @Test void sendWindowIsClaimedAtomicallyBeforeMail() {
        ValueOperations<String,String> values=mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(),eq("1"),eq(Duration.ofSeconds(60)))).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->otp.claimSend("u@example.test","RECOVERY")); verifyNoInteractions(mail,codes);
    }
}
