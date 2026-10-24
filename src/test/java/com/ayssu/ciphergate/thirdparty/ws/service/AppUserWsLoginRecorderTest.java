package com.ayssu.ciphergate.thirdparty.ws.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.service.AccessEventService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class AppUserWsLoginRecorderTest {
    @Test
    void httpIncrementsAtomicallyWithinAppAndNeverRecordsWsSession() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AppUser.class);
        var users = mock(AppUserMapper.class);
        var events = mock(AccessEventService.class);
        var recorder = new AppUserWsLoginRecorder(users, events);
        recorder.recordSuccessfulHttpLogin(3L, 7L, " 192.0.2.1 ", " device-1 ");
        ArgumentCaptor<LambdaUpdateWrapper<AppUser>> wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(users).update(isNull(), wrapper.capture());
        var update = wrapper.getValue();
        assertTrue(update.getSqlSet().contains("login_count = COALESCE(login_count, 0) + 1"));
        assertTrue(update.getSqlSegment().contains("app_id ="));
        assertTrue(update.getSqlSegment().contains("id ="));
        assertTrue(update.getParamNameValuePairs().containsValue(3L));
        assertTrue(update.getParamNameValuePairs().containsValue(7L));
        assertTrue(update.getParamNameValuePairs().containsValue("192.0.2.1"));
        assertTrue(update.getParamNameValuePairs().containsValue("device-1"));
        verifyNoInteractions(events);
    }

    @Test
    void wsStillRecordsItsOwnLoginEvent() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AppUser.class);
        var users = mock(AppUserMapper.class);
        var events = mock(AccessEventService.class);
        new AppUserWsLoginRecorder(users, events).recordSuccessfulLogin(3L, 7L, "192.0.2.1", "device-1");
        verify(users).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(events).recordAppUserWsLogin(3L, 7L);
        verifyNoMoreInteractions(events);
    }
}
