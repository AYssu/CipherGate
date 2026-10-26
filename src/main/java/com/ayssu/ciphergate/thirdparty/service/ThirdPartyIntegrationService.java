package com.ayssu.ciphergate.thirdparty.service;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.service.OpenTrialService;
import com.ayssu.ciphergate.service.PublicAbuseGuard;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartySignatureVerifier;
import com.ayssu.ciphergate.thirdparty.dto.ThirdPartyIntegrationRequest;
import com.ayssu.ciphergate.util.IntegrationIpPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Independent server credentials, no user passwords or browser challenges. */
@Service
@Slf4j
@RequiredArgsConstructor
public class ThirdPartyIntegrationService {
    public enum Action { TRIAL_APPLY, AUTH_QUERY }
    private final ThirdPartyCredentialMapper credentials;
    private final ThirdPartyCredentialAppMapper bindings;
    private final ApplicationMapper apps;
    private final ThirdPartyRechargeLogMapper logs;
    private final OpenTrialService trials;
    private final StringRedisTemplate redis;
    private final PublicAbuseGuard guard;
    private final PlatformTransactionManager transactionManager;
    private static final long SKEW = 300_000;
    private static final DefaultRedisScript<Long> DAILY = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],172800); end; return n", Long.class);

    /** Canonical values use LF separators; action, target and identity are all authenticated. */
    public static String signingText(Action action, ThirdPartyIntegrationRequest request) {
        return action.name()+"\n"+request.getApiKey().trim()+"\n"+request.getAppId()+"\n"
                +request.getUserEmail().trim().toLowerCase(Locale.ROOT)+"\n"+request.getTimestamp()+"\n"+request.getNonce();
    }
    private static class Rejected extends RuntimeException {
        final String code; final int status;
        Rejected(int status, String code, String message) { super(message); this.code=code; this.status=status; }
    }
    private static void reject(int status, String code, String message) { throw new Rejected(status,code,message); }

    public Result<?> execute(Action action, ThirdPartyIntegrationRequest request, String ip, String userAgent) {
        ThirdPartyRechargeLog row=new ThirdPartyRechargeLog();
        row.setOperation(action.name()); row.setDays(0); row.setRequestIp(ip); row.setSignValid(0);
        row.setIdempotentHit(0); row.setTraceId("tpi_"+UUID.randomUUID()); row.setCreatedAt(LocalDateTime.now());
        String dailyKey=null; boolean counted=false;
        try {
            if(request!=null) {
                row.setApiKey(request.getApiKey()==null?null:"sha256:"+PublicAbuseGuard.digest(request.getApiKey().trim()).substring(0,12));
                row.setRequestTs(request.getTimestamp());
            }
            guard.check("tp-integration-ip",ip,120,Duration.ofMinutes(1));
            validate(request);
            request.setApiKey(request.getApiKey().trim()); request.setUserEmail(request.getUserEmail().trim().toLowerCase(Locale.ROOT));
            // Only store validated, bounded identities. Never log secret, signature or request body.
            row.setUserEmail(request.getUserEmail());
            long now=System.currentTimeMillis();
            if(request.getTimestamp()<now-SKEW || request.getTimestamp()>now+SKEW)
                reject(401,"TIMESTAMP_EXPIRED","时间戳过期");
            ThirdPartyCredential credential=credentials.selectOne(new LambdaQueryWrapper<ThirdPartyCredential>()
                    .eq(ThirdPartyCredential::getApiKey,request.getApiKey()).eq(ThirdPartyCredential::getDeleted,0).last("LIMIT 1"));
            if(credential==null || !Integer.valueOf(1).equals(credential.getStatus())) reject(401,"BAD_API_KEY","凭证无效或已禁用");
            row.setCredentialId(credential.getId());
            guard.check("tp-integration-key",credential.getId().toString(),120,Duration.ofMinutes(1));
            checkCredential(credential,action,ip);
            if(!bindings.isAppBound(credential.getId(),request.getAppId())) reject(403,"APP_DENIED","凭证未授权该应用");
            // Unauthenticated callers cannot inject logs into another owner's application view.
            String expected=ThirdPartySignatureVerifier.hmacSha256Hex(credential.getApiSecret(),signingText(action,request));
            if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),request.getSign().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))
                reject(401,"BAD_SIGN","签名验证失败");
            row.setSignValid(1); row.setAppId(request.getAppId());
            String replay="cg:tp:integration:nonce:"+credential.getId()+":"+PublicAbuseGuard.digest(request.getNonce());
            if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(replay,"1",Duration.ofMinutes(11))))
                reject(409,"REPLAY","请求已使用，请生成新的nonce");
            dailyKey="cg:tp:recharge:daily:"+credential.getId()+":"+LocalDate.now();
            Long count=redis.execute(DAILY,List.of(dailyKey));
            if(count==null) reject(503,"LIMIT_UNAVAILABLE","调用计数暂不可用");
            counted=true;
            if(credential.getDailyLimit()!=null && credential.getDailyLimit()>0 && count>credential.getDailyLimit())
                reject(429,"DAILY_LIMIT_EXCEEDED","超过每日调用限制");
            Object result=new TransactionTemplate(transactionManager).execute(status->{
                // Serialize credential changes, quota checks and business writes with admin updates.
                ThirdPartyCredential current=credentials.selectForUpdate(credential.getId());
                if(current==null || !Integer.valueOf(1).equals(current.getStatus())) reject(401,"BAD_API_KEY","凭证无效或已禁用");
                checkCredential(current,action,ip);
                if(!Objects.equals(current.getApiSecret(),credential.getApiSecret())) reject(401,"BAD_SIGN","凭证已轮换，请重新签名");
                if(!bindings.isAppBound(current.getId(),request.getAppId())) reject(403,"APP_DENIED","凭证未授权该应用");
                Application app=apps.selectById(request.getAppId());
                if(app==null || Integer.valueOf(1).equals(app.getDeleted()) || !Integer.valueOf(1).equals(app.getStatus()))
                    reject(403,"APP_UNAVAILABLE","应用不存在或未启用");
                if(current.getDailyLimit()!=null && current.getDailyLimit()>0 && count>current.getDailyLimit())
                    reject(429,"DAILY_LIMIT_EXCEEDED","超过每日调用限制");
                if(current.getTotalCallLimit()!=null && current.getTotalCallLimit()>0 && current.getUsedCallCount()!=null && current.getUsedCallCount()>=current.getTotalCallLimit())
                    reject(429,"TOTAL_CALL_LIMIT_EXCEEDED","超过总调用限制");
                Object data=action==Action.TRIAL_APPLY
                        ?trials.applyAuthorizedTrial(request.getAppId(),request.getUserEmail(),ip,userAgent)
                        :trials.queryAuthorizedExpireAt(request.getAppId(),request.getUserEmail());
                if(credentials.update(null,new LambdaUpdateWrapper<ThirdPartyCredential>().eq(ThirdPartyCredential::getId,current.getId())
                        .setSql("used_call_count = COALESCE(used_call_count,0) + 1"))!=1)
                    throw new IllegalStateException("Credential usage update failed");
                row.setStatus(1);
                if(data instanceof OpenTrialService.TrialExpireResult expiry) row.setAfterExpiresAt(expiry.expiresAt());
                if(data instanceof OpenTrialService.TrialApplyResult trial) row.setAfterExpiresAt(trial.expiresAt());
                logs.insert(row);
                return data;
            });
            counted=false; // successful requests share the credential's daily/total call budgets with recharge
            return Result.success(result);
        } catch(Rejected failure) { return failure(row,failure.status,failure.code,failure.getMessage()); }
        catch(ResponseStatusException limited) { return failure(row,limited.getStatusCode().value(),"RATE_LIMITED","请求过于频繁，请稍后重试"); }
        catch(IllegalArgumentException invalid) { return failure(row,400,"INVALID_REQUEST",invalid.getMessage()); }
        catch(IllegalStateException conflict) { return failure(row,409,"BUSINESS_CONFLICT","该操作当前不可用或用户已领取试用"); }
        catch(Exception unavailable) {
            log.error("Third-party integration failed: traceId={}, errorType={}",row.getTraceId(),unavailable.getClass().getSimpleName());
            return failure(row,503,"SYSTEM_ERROR","服务暂不可用");
        } finally {
            if(counted && dailyKey!=null) {
                try {redis.opsForValue().decrement(dailyKey);} catch(Exception ignored) {log.warn("Integration daily quota release failed: traceId={}",row.getTraceId());}
            }
        }
    }
    private Result<?> failure(ThirdPartyRechargeLog row,int status,String code,String message) {
        row.setId(null); row.setStatus(2); row.setErrorCode(code); row.setErrorMessage(message);
        // Outside the business transaction so rejected and rolled-back calls remain observable.
        logs.insert(row);
        return Result.error(status,message);
    }
    private static void checkCredential(ThirdPartyCredential credential,Action action,String ip) {
        if(credential.getExpiresAt()!=null && !credential.getExpiresAt().isAfter(LocalDateTime.now())) reject(403,"CREDENTIAL_EXPIRED","凭证已过期");
        if(!(action==Action.TRIAL_APPLY?Boolean.TRUE.equals(credential.getAllowTrial()):Boolean.TRUE.equals(credential.getAllowQuery())))
            reject(403,"OPERATION_DENIED","凭证未开通该接口");
        if(!IntegrationIpPolicy.allows(credential.getAllowedIps(),ip)) reject(403,"IP_DENIED","IP地址未授权或白名单为空");
    }
    private static void validate(ThirdPartyIntegrationRequest request) {
        if(request==null || request.getApiKey()==null || !request.getApiKey().trim().matches("[A-Za-z0-9_]{1,100}")
                ||request.getAppId()==null||request.getAppId()<=0||request.getTimestamp()==null
                ||request.getUserEmail()==null||request.getUserEmail().length()>100
                ||!request.getUserEmail().trim().matches("[^\\s@\\r\\n]+@[^\\s@\\r\\n]+\\.[^\\s@\\r\\n]+")
                ||request.getNonce()==null||!request.getNonce().matches("[A-Za-z0-9_-]{16,64}")
                ||request.getSign()==null||!request.getSign().matches("[A-Fa-f0-9]{64}"))
            reject(400,"INVALID_REQUEST","请提供有效apiKey、appId、userEmail、timestamp、nonce及sign");
    }
}
