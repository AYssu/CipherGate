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
import org.springframework.transaction.annotation.Transactional;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartyRechargeServiceImpl implements ThirdPartyRechargeService {

    private static final long MAX_SKEW_MS = 5 * 60 * 1000L;
    private static final Duration IDEMPOTENT_TTL = Duration.ofHours(24);

    private final ThirdPartyCredentialMapper credentialMapper;
    private final ThirdPartyRechargeLogMapper rechargeLogMapper;
    private final AppUserMapper appUserMapper;
    private final ApplicationMapper applicationMapper;
    private final StringRedisTemplate redisTemplate;
    private final ActivityLogService activityLogService;
    private final SystemMessageService systemMessageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> recharge(ThirdPartyRechargeDTO dto, String ipAddress, String userAgent) {
        ThirdPartyRechargeLog logRow = initLog(dto, ipAddress);
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
            logRow.setApiKey(credential.getApiKey());

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

            String sign = generateSign(dto.getApiKey(), dto.getUserEmail(), dto.getProjectKey(), dto.getDays(), dto.getTimestamp(), credential.getApiSecret());
            if (!sign.equalsIgnoreCase(trim(dto.getSign()))) {
                logRow.setSignValid(0);
                return fail(logRow, "BAD_SIGN", "签名验证失败");
            }
            logRow.setSignValid(1);

            // 业务幂等：同凭证 + outTradeNo 只成功一次
            if (StringUtils.hasText(dto.getOutTradeNo())) {
                String outTradeNo = dto.getOutTradeNo().trim();
                String redisKey = "cg:tp:recharge:idempotent:" + credential.getId() + ":" + outTradeNo;
                Boolean fresh = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", IDEMPOTENT_TTL);
                if (Boolean.FALSE.equals(fresh)) {
                    logRow.setIdempotentHit(1);
                    return fail(logRow, "ORDER_ALREADY_PROCESSED", "订单已处理，请勿重复提交");
                }
                long history = rechargeLogMapper.selectCount(new LambdaQueryWrapper<ThirdPartyRechargeLog>()
                        .eq(ThirdPartyRechargeLog::getCredentialId, credential.getId())
                        .eq(ThirdPartyRechargeLog::getOutTradeNo, outTradeNo)
                        .eq(ThirdPartyRechargeLog::getStatus, 1));
                if (history > 0) {
                    logRow.setIdempotentHit(1);
                    writeLog(logRow, 2, "ORDER_ALREADY_PROCESSED", "订单已处理，请勿重复提交");
                    return Result.success("订单已处理", Map.of("alreadyProcessed", true));
                }
            }

            String dailyKey = "cg:tp:recharge:daily:" + credential.getId() + ":" + LocalDate.now();
            Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
            if (dailyCount != null && dailyCount == 1) {
                redisTemplate.expire(dailyKey, Duration.ofDays(2));
            }
            if (credential.getDailyLimit() != null && credential.getDailyLimit() > 0 && dailyCount != null && dailyCount > credential.getDailyLimit()) {
                redisTemplate.opsForValue().decrement(dailyKey);
                return fail(logRow, "DAILY_LIMIT_EXCEEDED", "超过每日调用限制");
            }

            long usedCalls = credential.getUsedCallCount() == null ? 0L : credential.getUsedCallCount();
            long usedDays = credential.getUsedDaysCount() == null ? 0L : credential.getUsedDaysCount();
            if (credential.getTotalCallLimit() != null && credential.getTotalCallLimit() > 0 && usedCalls >= credential.getTotalCallLimit()) {
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

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime before = appUser.getMemberExpiresAt();
            LocalDateTime base = before;
            if (base == null || !base.isAfter(now)) {
                base = now;
            }
            LocalDateTime after = base.plus(dto.getDays(), ChronoUnit.DAYS);
            appUser.setMemberExpiresAt(after);
            appUser.setUpdatedAt(now);
            appUserMapper.updateById(appUser);

            credentialMapper.update(null, new LambdaUpdateWrapper<ThirdPartyCredential>()
                    .eq(ThirdPartyCredential::getId, credential.getId())
                    .setSql("used_call_count = COALESCE(used_call_count,0) + 1")
                    .setSql("used_days_count = COALESCE(used_days_count,0) + " + Math.max(1, dto.getDays())));

            logRow.setBeforeExpiresAt(before);
            logRow.setAfterExpiresAt(after);
            writeLog(logRow, 1, null, "充值成功");
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
            data.put("userEmail", appUser.getEmail());
            data.put("days", dto.getDays());
            data.put("beforeExpiresAt", before);
            data.put("afterExpiresAt", after);
            return Result.success("充值成功", data);
        } catch (DuplicateKeyException e) {
            log.warn("third party recharge duplicate: {}", e.getMessage());
            writeLog(logRow, 2, "ORDER_ALREADY_PROCESSED", "订单已处理，请勿重复提交");
            return Result.error("订单已处理，请勿重复提交");
        } catch (Exception e) {
            log.error("third party recharge failed", e);
            writeLog(logRow, 2, "SYSTEM_ERROR", "系统异常: " + e.getMessage());
            return Result.error("系统异常: " + e.getMessage());
        }
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
        rechargeLogMapper.insert(row);
    }

    private ThirdPartyRechargeLog initLog(ThirdPartyRechargeDTO dto, String ipAddress) {
        ThirdPartyRechargeLog row = new ThirdPartyRechargeLog();
        row.setApiKey(trim(dto.getApiKey()));
        row.setUserEmail(trim(dto.getUserEmail()));
        row.setDays(dto.getDays());
        row.setOutTradeNo(trim(dto.getOutTradeNo()));
        row.setRequestIp(trim(ipAddress));
        row.setRequestTs(dto.getTimestamp());
        row.setSignValid(0);
        row.setIdempotentHit(0);
        row.setTraceId("tp_" + System.currentTimeMillis());
        return row;
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
}
