package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartySignatureVerifier;
import com.ayssu.ciphergate.thirdparty.dto.ThirdPartyIntegrationRequest;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyIntegrationService;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyIntegrationService.Action;
import com.ayssu.ciphergate.util.IntegrationIpPolicy;
import com.baomidou.mybatisplus.core.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.*;
import org.springframework.transaction.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;

class ThirdPartyIntegrationServiceTest {
    ThirdPartyCredentialMapper keys;ThirdPartyCredentialAppMapper bindings;ApplicationMapper apps;ThirdPartyRechargeLogMapper logs;
    OpenTrialService trials;StringRedisTemplate redis;ValueOperations<String,String> values;ThirdPartyCredential key;ThirdPartyIntegrationService service;
    ThirdPartyIntegrationRequest request;
    @BeforeAll static void metadata(){
        var config=new MybatisConfiguration();
        for(var type:java.util.List.of(ThirdPartyCredential.class,ThirdPartyRechargeLog.class))
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new MapperBuilderAssistant(config,"integration-unit"),type);
    }
    @BeforeEach @SuppressWarnings("unchecked") void setup(){
        keys=mock(ThirdPartyCredentialMapper.class);bindings=mock(ThirdPartyCredentialAppMapper.class);apps=mock(ApplicationMapper.class);logs=mock(ThirdPartyRechargeLogMapper.class);
        trials=mock(OpenTrialService.class);redis=mock(StringRedisTemplate.class);values=mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(),eq("1"),any(Duration.class))).thenReturn(true);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList())).thenReturn(1L);
        var tx=mock(PlatformTransactionManager.class);when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service=new ThirdPartyIntegrationService(keys,bindings,apps,logs,trials,redis,mock(PublicAbuseGuard.class),tx);
        key=new ThirdPartyCredential();key.setId(5L);key.setApiSecret("test-secret");key.setStatus(1);key.setAllowQuery(true);key.setAllowTrial(true);key.setAllowedIps("192.0.2.1");
        when(keys.selectOne(any())).thenReturn(key);when(keys.selectForUpdate(5L)).thenReturn(key);when(keys.update(isNull(),any())).thenReturn(1);
        when(bindings.isAppBound(5L,10L)).thenReturn(true);
        var app=new Application();app.setId(10L);app.setStatus(1);when(apps.selectById(10L)).thenReturn(app);
        request=new ThirdPartyIntegrationRequest();request.setApiKey("tpk_test");request.setAppId(10L);request.setUserEmail("User@Example.test");request.setTimestamp(System.currentTimeMillis());request.setNonce("test-nonce-123456789");sign(Action.AUTH_QUERY);
    }
    void sign(Action action){request.setSign(ThirdPartySignatureVerifier.hmacSha256Hex(key.getApiSecret(),ThirdPartyIntegrationService.signingText(action,request)));}
    ThirdPartyRechargeLog lastLog(){var cap=ArgumentCaptor.forClass(ThirdPartyRechargeLog.class);verify(logs,atLeastOnce()).insert(cap.capture());return cap.getValue();}
    @Test void queryNeedsOnlyEmailAndCredentialEnvelope(){
        when(trials.queryAuthorizedExpireAt(10L,"user@example.test")).thenReturn(new OpenTrialService.TrialExpireResult(10L,LocalDateTime.now()));
        var result=service.execute(Action.AUTH_QUERY,request,"192.0.2.1","test");assertTrue(result.getSuccess());
        assertEquals("AUTH_QUERY",lastLog().getOperation());assertEquals(1,lastLog().getSignValid());assertEquals(1,lastLog().getStatus());
        verify(trials).queryAuthorizedExpireAt(10L,"user@example.test");assertNotEquals(request.getApiKey(),lastLog().getApiKey());
    }
    @Test void trialHasSeparatePermissionAndSignature(){
        sign(Action.TRIAL_APPLY);when(trials.applyAuthorizedTrial(anyLong(),anyString(),anyString(),any())).thenReturn(new OpenTrialService.TrialApplyResult(10L,1,LocalDateTime.now()));
        assertTrue(service.execute(Action.TRIAL_APPLY,request,"192.0.2.1",null).getSuccess());verify(trials).applyAuthorizedTrial(10L,"user@example.test","192.0.2.1",null);
    }
    @Test void querySignatureCannotBeUsedToApplyTrial(){
        assertEquals(401,service.execute(Action.TRIAL_APPLY,request,"192.0.2.1",null).getCode());assertEquals("BAD_SIGN",lastLog().getErrorCode());verifyNoInteractions(trials);
    }
    @Test void missingKeyLogsFingerprintAndIpWithoutAssigningUntrustedApp(){
        when(keys.selectOne(any())).thenReturn(null);
        assertEquals(401,service.execute(Action.AUTH_QUERY,request,"192.0.2.1",null).getCode());
        assertEquals("BAD_API_KEY",lastLog().getErrorCode());assertEquals("192.0.2.1",lastLog().getRequestIp());assertNull(lastLog().getAppId());
    }
    @Test void deniedIpAndEmptyWhitelistFailBeforeBusiness(){
        assertEquals(403,service.execute(Action.AUTH_QUERY,request,"192.0.2.2",null).getCode());assertEquals("IP_DENIED",lastLog().getErrorCode());verifyNoInteractions(trials);
        key.setAllowedIps(null);assertEquals(403,service.execute(Action.AUTH_QUERY,request,"192.0.2.1",null).getCode());
    }
    @Test void legacyKeyDoesNotAutomaticallyReceiveNewPermissions(){
        key.setAllowQuery(null);assertEquals(403,service.execute(Action.AUTH_QUERY,request,"192.0.2.1",null).getCode());assertEquals("OPERATION_DENIED",lastLog().getErrorCode());
    }
    @Test void unboundAppCannotBeQueried(){
        request.setAppId(20L);sign(Action.AUTH_QUERY);assertEquals(403,service.execute(Action.AUTH_QUERY,request,"192.0.2.1",null).getCode());assertEquals("APP_DENIED",lastLog().getErrorCode());verifyNoInteractions(trials);
    }
    @Test void replayIsRejectedAndAudited(){
        when(values.setIfAbsent(anyString(),eq("1"),any(Duration.class))).thenReturn(false);
        assertEquals(409,service.execute(Action.AUTH_QUERY,request,"192.0.2.1",null).getCode());assertEquals("REPLAY",lastLog().getErrorCode());verifyNoInteractions(trials);
    }
    @Test void expiredAndMalformedRequestsAreAudited(){
        request.setTimestamp(Long.MIN_VALUE);assertEquals(401,service.execute(Action.AUTH_QUERY,request,"192.0.2.1",null).getCode());
        assertEquals(400,service.execute(Action.AUTH_QUERY,null,"192.0.2.1",null).getCode());
    }
    @Test void totalQuotaAndDisabledApplicationPreventBusiness(){
        key.setTotalCallLimit(1L);key.setUsedCallCount(1L);assertEquals(429,service.execute(Action.AUTH_QUERY,request,"192.0.2.1",null).getCode());verifyNoInteractions(trials);
        verify(values).decrement(startsWith("cg:tp:recharge:daily:"));
    }
    @Test void failureDoesNotConsumeDailyQuotaOrUsage(){
        when(trials.queryAuthorizedExpireAt(anyLong(),anyString())).thenThrow(new IllegalArgumentException("目标用户不存在"));
        assertEquals(400,service.execute(Action.AUTH_QUERY,request,"192.0.2.1",null).getCode());verify(values).decrement(anyString());verify(keys,never()).update(isNull(),any());
    }
    @Test void literalIpsNormalizeIpv6AndRejectHostnamesOrWildcard(){
        assertTrue(IntegrationIpPolicy.allows("2001:db8::1","2001:0db8:0:0:0:0:0:1"));
        for(String invalid:java.util.List.of("localhost","0.0.0.0/0","*","127.1","192.000.2.1","999.1.1.1"))assertFalse(IntegrationIpPolicy.valid(invalid),invalid);
    }
}
