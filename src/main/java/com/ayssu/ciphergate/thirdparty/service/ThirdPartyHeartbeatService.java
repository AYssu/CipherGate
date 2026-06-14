package com.ayssu.ciphergate.thirdparty.service;

import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.thirdparty.dto.HeartbeatResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 卡密心跳服务。
 * <p>
 * Redis 结构（卡密为主键）：
 * <pre>
 *   cg:hb:card:{cardId}  →  JSON payload（含 appId, cardCode, deviceId, lastHeartbeatAt, token）
 *   cg:hb:token:{token}  →  cardId（反向查找：token → 卡密）
 *   cg:hb:rl:{cardId}    →  上次心跳时间戳 ms（限流，按卡密维度）
 * </pre>
 * <p>
 * 心跳交换时只更新 cg:hb:card:{cardId} 的 token 值，旧 token 自动失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartyHeartbeatService {

    private static final String HB_CARD_PREFIX = "cg:hb:card:";   // cardId → payload
    private static final String HB_TOKEN_PREFIX = "cg:hb:token:"; // token → cardId
    private static final String HB_RL_PREFIX = "cg:hb:rl:";       // cardId → lastMs（限流）
    private static final long MIN_INTERVAL_SECONDS = 30;
    private static final long SHARE_DETECT_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final LicenseKeyMapper licenseKeyMapper;
    private final ThirdPartyAppVariableService thirdPartyAppVariableService;
    private final SecureRandom random = new SecureRandom();

    /**
     * 登录成功后调用，生成心跳 token 并存储。
     *
     * @return 心跳 token
     */
    public String storeToken(Long appId, Long cardId, String cardCode, String deviceId, LocalDateTime expiresAt) {
        // 同一张卡重新登录 → 旧 token 自动失效（覆盖 cardId 映射即可）
        invalidateOldToken(cardId);

        String token = generateToken();
        Duration ttl = calcTtl(expiresAt);

        // 构建 payload
        JSONObject payload = new JSONObject();
        payload.put("appId", appId);
        payload.put("cardId", cardId);
        payload.put("cardCode", cardCode);
        payload.put("deviceId", deviceId);
        payload.put("token", token);
        payload.put("lastHeartbeatAt", null);

        // cardId → payload（主键）
        redisTemplate.opsForValue().set(HB_CARD_PREFIX + cardId, payload.toJSONString(), ttl);
        // token → cardId（反向索引）
        redisTemplate.opsForValue().set(HB_TOKEN_PREFIX + token, String.valueOf(cardId), ttl);

        log.debug("心跳 token 已存储: cardId={}, ttl={}s", cardId, ttl.getSeconds());
        return token;
    }

    /**
     * 心跳交换：校验 token → 限流 → 更新卡密 payload 中的 token → 返回新数据。
     */
    public HeartbeatResponse exchange(String token) {
        if (!StringUtils.hasText(token)) {
            throw new RuntimeException("token 不能为空");
        }
        String trimmed = token.trim();

        // 1. token → cardId
        String cardIdStr = redisTemplate.opsForValue().get(HB_TOKEN_PREFIX + trimmed);
        if (cardIdStr == null) {
            throw new RuntimeException("token 无效或已过期");
        }
        Long cardId = Long.parseLong(cardIdStr);

        // 2. cardId → payload
        String cardKey = HB_CARD_PREFIX + cardId;
        String json = redisTemplate.opsForValue().get(cardKey);
        if (json == null) {
            throw new RuntimeException("token 无效或已过期");
        }
        JSONObject payload = JSON.parseObject(json);

        // 3. 校验 token 是否是当前有效的（防止用旧 token）
        if (!trimmed.equals(payload.getString("token"))) {
            throw new RuntimeException("token 已失效，请重新登录");
        }

        // 4. 30 秒限流（按卡密维度）
        String rlKey = HB_RL_PREFIX + cardId;
        String lastMs = redisTemplate.opsForValue().get(rlKey);
        if (lastMs != null) {
            long lastTime = Long.parseLong(lastMs);
            long now = Instant.now().toEpochMilli();
            if (now - lastTime < MIN_INTERVAL_SECONDS * 1000L) {
                long waitSec = MIN_INTERVAL_SECONDS - (now - lastTime) / 1000;
                throw new RuntimeException("心跳过于频繁，请 " + waitSec + " 秒后重试");
            }
        }

        // 5. 检测疑似多用户（心跳间隔 > 60 秒）
        boolean potentiallyShared = false;
        String lastHbStr = payload.getString("lastHeartbeatAt");
        if (lastHbStr != null) {
            try {
                LocalDateTime lastHb = LocalDateTime.parse(lastHbStr);
                long gapSeconds = ChronoUnit.SECONDS.between(lastHb, LocalDateTime.now());
                if (gapSeconds > SHARE_DETECT_SECONDS) {
                    potentiallyShared = true;
                }
            } catch (Exception ignored) {
            }
        }

        // 6. 生成新 token，更新 payload
        String newToken = generateToken();
        payload.put("token", newToken);
        payload.put("lastHeartbeatAt", LocalDateTime.now().toString());

        // 7. 重新计算 TTL
        Duration ttl = calcTtlFromCardId(cardId);

        // 8. 更新 cardId → payload（只更新这一个 key，旧 token 自动失效）
        redisTemplate.opsForValue().set(cardKey, payload.toJSONString(), ttl);
        // 新增 token → cardId 映射
        redisTemplate.opsForValue().set(HB_TOKEN_PREFIX + newToken, String.valueOf(cardId), ttl);
        // 旧 token → cardId 映射等自然过期（或主动删除）
        redisTemplate.delete(HB_TOKEN_PREFIX + trimmed);

        // 9. 限流 key
        redisTemplate.opsForValue().set(rlKey, String.valueOf(Instant.now().toEpochMilli()), 60, TimeUnit.SECONDS);

        // 10. 获取应用变量
        Long appId = payload.getLong("appId");
        String deviceId = payload.getString("deviceId");
        AppVariableTemplateContext variableCtx = new AppVariableTemplateContext();
        variableCtx.setDeviceId(deviceId);
        Map<String, Object> variables = thirdPartyAppVariableService.getEnabledVariablesMap(appId, variableCtx);

        // 11. 构建响应
        LicenseKey key = licenseKeyMapper.selectById(cardId);
        HeartbeatResponse resp = new HeartbeatResponse();
        resp.setAppId(appId);
        resp.setCardId(cardId);
        resp.setCardCode(payload.getString("cardCode"));
        resp.setNewToken(newToken);
        resp.setExpiresAt(key != null ? key.getExpiresAt() : null);
        resp.setAvailable(resolveAvailableSeconds(key != null ? key.getExpiresAt() : null));
        resp.setVariables(JSON.toJSONString(variables));
        resp.setOnline(key != null && Boolean.TRUE.equals(key.getIsOnline()));
        resp.setPotentiallyShared(potentiallyShared);

        log.debug("心跳交换完成: cardId={}, potentiallyShared={}", cardId, potentiallyShared);
        return resp;
    }

    /**
     * 废弃旧 token：覆盖 cardId 映射即可，旧 token 自然失效
     */
    private void invalidateOldToken(Long cardId) {
        if (cardId == null) return;
        String cardKey = HB_CARD_PREFIX + cardId;
        String oldPayload = redisTemplate.opsForValue().get(cardKey);
        if (oldPayload != null) {
            try {
                JSONObject old = JSON.parseObject(oldPayload);
                String oldToken = old.getString("token");
                if (oldToken != null) {
                    redisTemplate.delete(HB_TOKEN_PREFIX + oldToken);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private Duration calcTtl(LocalDateTime expiresAt) {
        Duration ttl = (expiresAt != null)
                ? Duration.between(LocalDateTime.now(), expiresAt)
                : Duration.ofHours(2);
        return ttl.getSeconds() < 60 ? Duration.ofSeconds(60) : ttl;
    }

    private Duration calcTtlFromCardId(Long cardId) {
        LicenseKey key = licenseKeyMapper.selectById(cardId);
        return calcTtl(key != null ? key.getExpiresAt() : null);
    }

    private String generateToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private Long resolveAvailableSeconds(LocalDateTime expiresAt) {
        if (expiresAt == null) return null;
        return Math.max(0L, Duration.between(LocalDateTime.now(), expiresAt).getSeconds());
    }
}
