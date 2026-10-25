package com.ayssu.ciphergate.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ayssu.ciphergate.dto.PublicLicenseUnbindRequest;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.service.impl.PublicLicenseSelfServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicLicenseSelfServiceLoggingTest {
    @Test void successfulUnbindLogsOnlyRecordIdentityNotSecretCard() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "log-test"), LicenseKey.class);
        LicenseKeyMapper keys = mock(LicenseKeyMapper.class);
        ApplicationMapper apps = mock(ApplicationMapper.class);
        LicenseKey key = new LicenseKey(); key.setId(9L); key.setAppId(10L);
        key.setKeyCode("PRIVATE-UNBIND-CARD-837263"); key.setBindDeviceId("device-secret");
        key.setUnbindCount(0); key.setUnbindLimit(3);
        when(keys.selectOne(any())).thenReturn(key);
        when(apps.selectById(10L)).thenReturn(new Application());
        PublicLicenseUnbindRequest request = new PublicLicenseUnbindRequest();
        request.setAppId(10L); request.setKeyCode(key.getKeyCode()); request.setUnbindDevice(true); request.setUnbindIp(false);
        Logger logger = (Logger) LoggerFactory.getLogger(PublicLicenseSelfServiceImpl.class);
        var oldLevel = logger.getLevel(); logger.setLevel(ch.qos.logback.classic.Level.INFO);
        ListAppender<ILoggingEvent> capture = new ListAppender<>(); capture.start(); logger.addAppender(capture);
        try {
            new PublicLicenseSelfServiceImpl(keys, apps).unbind(request);
            assertFalse(capture.list.isEmpty());
            String output = capture.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a,b) -> a+"\n"+b);
            assertTrue(output.contains("keyId=9")); assertFalse(output.contains(key.getKeyCode()));
            assertFalse(output.contains("device-secret"));
        } finally { logger.detachAppender(capture); capture.stop(); logger.setLevel(oldLevel); }
    }
}
