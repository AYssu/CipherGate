package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PortalRecoveryDispatcherTest {
    AppUserMapper users=mock(AppUserMapper.class);PortalOtpService otp=mock(PortalOtpService.class);ExecutorService executor=mock(ExecutorService.class);
    PortalRecoveryDispatcher dispatcher=new PortalRecoveryDispatcher(users,otp,executor);
    @BeforeEach void setup(){TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"test"),AppUser.class);}
    @Test void requestReturnsBeforeIdentityLookupOrMailAndMissingIdentityDoesNotSend(){
        dispatcher.dispatch("unknown@example.test");verifyNoInteractions(users,otp);
        var queued=ArgumentCaptor.forClass(Runnable.class);verify(executor).execute(queued.capture());
        when(users.selectCount(any())).thenReturn(0L);queued.getValue().run();verifyNoInteractions(otp);
    }
    @Test void knownIdentityAlsoEnqueuesAndSendsOnlyWhenWorkerRuns(){
        dispatcher.dispatch("known@example.test");verifyNoInteractions(users,otp);
        var queued=ArgumentCaptor.forClass(Runnable.class);verify(executor).execute(queued.capture());
        when(users.selectCount(any())).thenReturn(1L);queued.getValue().run();verify(otp).send("known@example.test","RECOVERY");
    }
    @Test void boundedQueueFailureHasGenericServiceUnavailableStatus(){
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->dispatcher.dispatch("u@example.test"));assertEquals(503,error.getStatusCode().value());verifyNoInteractions(users,otp);
    }
}
