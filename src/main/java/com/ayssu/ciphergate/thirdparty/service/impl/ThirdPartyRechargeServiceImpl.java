package com.ayssu.ciphergate.thirdparty.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.ThirdPartyCredential;
import com.ayssu.ciphergate.entity.ThirdPartyRechargeLog;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.ThirdPartyCredentialMapper;
import com.ayssu.ciphergate.mapper.ThirdPartyRechargeLogMapper;
import com.ayssu.ciphergate.service.ActivityLogService;
import com.ayssu.ciphergate.service.SystemMessageService;
import com.ayssu.ciphergate.thirdparty.dto.ThirdPartyRechargeDTO;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyRechargeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 三方加时服务。
 *
 * <p>事务边界只包裹核心数据库写入：任何业务异常都会真正回滚；
 * 校验失败、Redis 幂等锁和每日计数在事务外清理，避免失败调用占用订单或污染额度。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartyRechargeServiceImpl implements ThirdPartyRechargeService {

    private static final long MAX_SKEW_MS = 5 * 60 * 1000L;
    private static final Duration IDEMPOTENT_TTL = Duration.ofHours(24);
    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCESS = "SUCCESS";

    private final ThirdPartyCredentialMapper credentialMapper;
    private final ThirdPartyRechargeLogMapper rechargeLogMapper;
    private final AppUserMapper appUserMapper;
    private final ApplicationMapper applicationMapper;
    private final StringRedisTemplate redisTemplate;
    private final ActivityLogService activityLogService;
    private final SystemMessageService systemMessageService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public Result<?> recharge(ThirdPartyRechargeDTO dto, String ipAddress, String userAgent) {
        ThirdPartyRechargeLog logRow = initLog(dto, ipAddress);
        String orderNo = trim(dto.getOutTradeNo());
        String idempotentKey = null;
        boolean lockAcquired = false;
        boolean dailyCounted = false;
        boolean succeeded = false;
        String dailyKey = null;

        try {
            long nowMs = System.currentTimeMillis();
            if (dto.getTimestamp() == null || Math.abs(nowMs - dto.getTimestamp()) > MAX_SKEW_MS) {
                return fail(logRow, "TIMESTAMP_EXPIRED", "时间戳过期，请求无效");
            }

            ThirdPartyCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<ThirdPartyCredential>()
                    .eq(ThirdPartyCredential::getApiKey, trim(dto.getApiKey()))
                    .eq(ThirdPartyCredential::getDeleted, 0)
                    .last("limit 1"));
            if (credential == null || credential.getStatus() == null || credential.getStatus() != 1) {
                return fail(logRow, "BAD_API_KEY", "API Key无效或已禁用");
            }
            logRow.setCredentialId(credential.getId());
            logRow.setApiKey(apiKeyFingerprint(credential.getApiKey()));

            if (credential.getExpiresAt() != null && credential.getExpiresAt().isBefore(LocalDateTime.now())) {
                return fail(logRow, "CREDENTIAL_EXPIRED", "API凭证已过期");
            }
            if (!ipAllowed(credential.getAllowedIps(), ipAddress)) {
                return fail(logRow, "IP_DENIED", "IP地址未授权");
            }

            Long appId = decodeAppId(dto.getProjectKey());
            if (appId == null) {
                return fail(logRow, "BAD_PROJECT_KEY", "projectKey解析失败");
            }
            logRow.setAppId(appId);
            if (!appId.equals(credential.getAppId())) {
                return fail(logRow, "APP_DENIED", "无权限操作该应用");
            }

            Application app = applicationMapper.selectById(appId);
            if (app == null || (app.getDeleted() != null && app.getDeleted() == 1)) {
                return fail(logRow, "APP_NOT_FOUND", "应用不存在");
            }

            String sign = generateSign(dto.getApiKey(), dto.getUserEmail(), dto.getProjectKey(),
                    dto.getDays(), dto.getTimestamp(), credential.getApiSecret());
            if (!sign.equalsIgnoreCase(trim(dto.getSign()))) {
                logRow.setSignValid(0);
                return fail(logRow, "BAD_SIGN", "签名验证失败");
            }
            logRow.setSignValid(1);

            long usedCalls = credential.getUsedCallCount() == null ? 0L : credential.getUsedCallCount();
            long usedDays = credential.getUsedDaysCount() == null ? 0L : credential.getUsedDaysCount();
            if (credential.getTotalCallLimit() != null && credential.getTotalCallLimit() > 0
                    && usedCalls >= credential.getTotalCallLimit()) {
                return fail(logRow, "TOTAL_CALL_LIMIT_EXCEEDED", "超过总调用限制");
            }
            if (credential.getTotalDaysLimit() != null && credential.getTotalDaysLimit() > 0
                    && usedDays + dto.getDays() > credential.getTotalDaysLimit()) {
                return fail(logRow, "TOTAL_DAYS_LIMIT_EXCEEDED", "超过总消费天数限制");
            }

            AppUser appUser = appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                    .eq(AppUser::getAppId, appId)
                    .eq(AppUser::getEmail, dto.getUserEmail().trim())
                    .eq(AppUser::getDeleted, 0)
                    .last("limit 1"));
            if (appUser == null) {
                return fail(logRow, "APP_USER_NOT_FOUND", "目标用户不存在");
            }

            if (StringUtils.hasText(orderNo)) {
                long history = rechargeLogMapper.selectCount(new LambdaQueryWrapper<ThirdPartyRechargeLog>()
                        .eq(ThirdPartyRechargeLog::getCredentialId, credential.getId())
                        .eq(ThirdPartyRechargeLog::getOutTradeNo, orderNo)
                        .eq(ThirdPartyRechargeLog::getStatus, 1));
                if (history > 0) {
                    logRow.setIdempotentHit(1);
                    writeLog(logRow, 2, "ORDER_ALREADY_PROCESSED", "订单已处理，请勿重复提交");
                    return alreadyProcessed();
                }

                idempotentKey = "cg:tp:recharge:idempotent:" + credential.getId() + ":" + orderNo;
                Boolean fresh = redisTemplate.opsForValue().setIfAbsent(idempotentKey, PROCESSING, IDEMPOTENT_TTL);
                if (Boolean.FALSE.equals(fresh)) {
                    String current = redisTemplate.opsForValue().get(idempotentKey);
                    logRow.setIdempotentHit(1);
                    if (SUCCESS.equals(current)) {
                        writeLog(logRow, 2, "ORDER_ALREADY_PROCESSED", "订单已处理，请勿重复提交");
                        return alreadyProcessed();
                    }
                    return fail(logRow, "ORDER_IN_PROGRESS", "订单正在处理，请勿重复提交");
                }
                lockAcquired = true;
            }

            dailyKey = "cg:tp:recharge:daily:" + credential.getId() + ":" + LocalDate.now();
            Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
            dailyCounted = true;
            if (dailyCount != null && dailyCount == 1) {
                redisTemplate.expire(dailyKey, Duration.ofDays(2));
            }
            if (credential.getDailyLimit() != null && credential.getDailyLimit() > 0
                    && dailyCount != null && dailyCount > credential.getDailyLimit()) {
                redisTemplate.opsForValue().decrement(dailyKey);
                dailyCounted = false;
                return fail(logRow, "DAILY_LIMIT_EXCEEDED", "超过每日调用限制");
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime before = appUser.getMemberExpiresAt();
            LocalDateTime base = before;
            if (base == null || !base.isAfter(now)) {
                base = now;
            }
            LocalDateTime after = base.plus(dto.getDays(), ChronoUnit.DAYS);

            // 核心数据库写入放在独立事务中；异常会回滚，随后由外层写失败日志并释放 Redis。
            SuccessData successData = new TransactionTemplate(transactionManager).execute(status -> {
                AppUser currentUser = appUserMapper.selectById(appUser.getId());
                if (currentUser == null) {
                    throw new IllegalStateException("目标用户已不存在");
                }
                LocalDateTime currentNow = LocalDateTime.now();
                LocalDateTime currentBefore = currentUser.getMemberExpiresAt();
                LocalDateTime currentBase = currentBefore;
                if (currentBase == null || !currentBase.isAfter(currentNow)) {
                    currentBase = currentNow;
                }
                LocalDateTime currentAfter = currentBase.plus(dto.getDays(), ChronoUnit.DAYS);
                currentUser.setMemberExpiresAt(currentAfter);
                currentUser.setUpdatedAt(currentNow);
                appUserMapper.updateById(currentUser);

                credentialMapper.update(null, new LambdaUpdateWrapper<ThirdPartyCredential>()
                        .eq(ThirdPartyCredential::getId, credential.getId())
                        .setSql("used_call_count = COALESCE(used_call_count,0) + 1")
                        .setSql("used_days_count = COALESCE(used_days_count,0) + " + Math.max(1, dto.getDays())));

                logRow.setBeforeExpiresAt(currentBefore);
                logRow.setAfterExpiresAt(currentAfter);
                logRow.setOutTradeNo(orderNo);
                writeLog(logRow, 1, null, "充值成功");
                return new SuccessData(currentBefore, currentAfter, currentUser.getEmail());
            });
            if (successData == null) {
                throw new IllegalStateException("充值事务未完成");
            }

            succeeded = true;
            if (lockAcquired && idempotentKey != null) {
                try {
                    redisTemplate.opsForValue().set(idempotentKey, SUCCESS, IDEMPOTENT_TTL);
                } catch (Exception ex) {
                    // 核心充值已经提交，Redis 状态刷新失败不能把成功请求改判为失败；
                    // 下次请求仍可通过数据库成功记录识别重复订单。
                    log.warn("mark third party idempotent success failed: key={}, err={}", idempotentKey, ex.getMessage());
                }
            }

            try {
                activityLogService.log(
                        credential.getCreatedBy(),
                        "third_party",
                        "THIRD_PARTY_RECHARGE",
                        "APP_USER",
                        "三方凭证加时成功 appId=" + appId + ", email=" + dto.getUserEmail() + ", days=" + dto.getDays(),
                        ipAddress,
                        userAgent,
                        "SUCCESS",
                        "MEDIUM"
                );
            } catch (Exception ex) {
                log.warn("write third party recharge activity log failed: {}", ex.getMessage());
            }
            if (app.getOwnerId() != null) {
                try {
                    systemMessageService.createMessage(
                            "THIRD_PARTY_RECHARGE",
                            "三方凭证加时成功",
                            "应用ID=" + appId + "，用户=" + dto.getUserEmail() + "，加时=" + dto.getDays() + "天",
                            "MEDIUM",
                            "USER",
                            app.getOwnerId()
                    );
                } catch (Exception ex) {
                    log.warn("create system message failed: {}", ex.getMessage());
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("alreadyProcessed", false);
            data.put("userEmail", successData.email());
            data.put("days", dto.getDays());
            data.put("beforeExpiresAt", successData.before());
            data.put("afterExpiresAt", successData.after());
            return Result.success("充值成功", data);
        } catch (DuplicateKeyException e) {
            // 事务已回滚；让 finally 退回本次每日计数并释放本次锁，
            // 下一次请求会由数据库成功订单检查直接识别为已处理。
            log.warn("third party recharge duplicate: {}", e.getMessage());
            logRow.setIdempotentHit(1);
            writeLog(logRow, 2, "ORDER_ALREADY_PROCESSED", "订单已处理，请勿重复提交");
            return alreadyProcessed();
        } catch (Exception e) {
            log.error("third party recharge failed", e);
            if (!succeeded) {
                writeLog(logRow, 2, "SYSTEM_ERROR", "系统异常: " + e.getMessage());
            }
            return Result.error("系统异常: " + e.getMessage());
        } finally {
            if (!succeeded && dailyCounted && dailyKey != null) {
                try {
                    redisTemplate.opsForValue().decrement(dailyKey);
                } catch (Exception ex) {
                    log.warn("decrement third party daily counter failed: key={}, err={}", dailyKey, ex.getMessage());
                }
            }
            if (lockAcquired && !succeeded && idempotentKey != null) {
                try {
                    redisTemplate.delete(idempotentKey);
                } catch (Exception ex) {
                    log.warn("release third party idempotent lock failed: key={}, err={}", idempotentKey, ex.getMessage());
                }
            }
        }
    }

    private Result<?> alreadyProcessed() {
        return Result.success("订单已处理", Map.of("alreadyProcessed", true));
    }

    private Result<?> fail(ThirdPartyRechargeLog row, String errorCode, String message) {
        writeLog(row, 2, errorCode, message);
        return Result.error(message);
    }

    private void writeLog(ThirdPartyRechargeLog row, int status, String code, String msg) {
        if (row == null) {
            return;
        }
        row.setStatus(status);
        row.setErrorCode(code);
        row.setErrorMessage(msg);
        row.setCreatedAt(LocalDateTime.now());
        if (status != 1) {
            // unique(credential_id, out_trade_no) 只约束成功订单；
            // 失败订单不能占用唯一键，否则同一订单第一次失败后无法重试。
            row.setOutTradeNo(null);
        }
        rechargeLogMapper.insert(row);
    }

    private ThirdPartyRechargeLog initLog(ThirdPartyRechargeDTO dto, String ipAddress) {
        ThirdPartyRechargeLog row = new ThirdPartyRechargeLog();
        row.setApiKey(apiKeyFingerprint(dto.getApiKey()));
        row.setUserEmail(trim(dto.getUserEmail()));
        row.setDays(dto.getDays());
        row.setRequestIp(trim(ipAddress));
        row.setRequestTs(dto.getTimestamp());
        row.setSignValid(0);
        row.setIdempotentHit(0);
        row.setTraceId("tp_" + System.currentTimeMillis());
        return row;
    }

    /** 不可逆短指纹，足够关联日志，不保存原始 API Key。 */
    private String apiKeyFingerprint(String apiKey) {
        String value = trim(apiKey);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return "sha256:" + DigestUtil.sha256Hex(value).substring(0, 12);
    }

    private boolean ipAllowed(String allowedIps, String ipAddress) {
        if (!StringUtils.hasText(allowedIps)) {
            return true;
        }
        String ip = trim(ipAddress);
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        String[] arr = allowedIps.split(",");
        for (String item : arr) {
            if (ip.equals(item == null ? "" : item.trim())) {
                return true;
            }
        }
        return false;
    }

    private Long decodeAppId(String projectKey) {
        try {
            byte[] raw = Base64.getDecoder().decode(projectKey.trim());
            String s = new String(raw, StandardCharsets.UTF_8).trim();
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String generateSign(String apiKey, String userEmail, String projectKey,
                                Integer days, Long timestamp, String secret) {
        String signStr = String.format(Locale.ROOT,
                "apiKey=%s&days=%d&projectKey=%s&timestamp=%d&userEmail=%s&secret=%s",
                trim(apiKey), days, trim(projectKey), timestamp, trim(userEmail), trim(secret));
        return DigestUtil.md5Hex(signStr, StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
    }

    private String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private record SuccessData(LocalDateTime before, LocalDateTime after, String email) {
    }
}
