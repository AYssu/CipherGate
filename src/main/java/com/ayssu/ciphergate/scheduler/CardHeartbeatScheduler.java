package com.ayssu.ciphergate.scheduler;

import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 卡密心跳超时检测。
 * 扫描 Redis 中的心跳记录，超时则标记离线。
 * 不查 DB 在线列表，只处理有心跳记录的卡密。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardHeartbeatScheduler {

    private static final String HB_CARD_PREFIX = "cg:hb:card:";
    private static final String HB_TOKEN_PREFIX = "cg:hb:token:";
    private static final int TIMEOUT_MULTIPLIER = 3;
    private static final int DEFAULT_HEARTBEAT_INTERVAL = 60;

    private final LicenseKeyMapper licenseKeyMapper;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedRate = 60000)
    public void checkHeartbeatTimeout() {
        List<Long> timeoutCardIds = new ArrayList<>();

        // 只扫描 Redis 中有心跳记录的卡密（数据量可控）
        var keys = redisTemplate.keys(HB_CARD_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            try {
                Long cardId = parseCardId(key);
                if (cardId == null) continue;

                String json = redisTemplate.opsForValue().get(key);
                if (json == null) continue;

                JSONObject payload = JSON.parseObject(json);
                String lastHbStr = payload.getString("lastHeartbeatAt");

                // 从未收到心跳，等 Redis key 自然过期
                if (lastHbStr == null) continue;

                LocalDateTime lastHb = LocalDateTime.parse(lastHbStr);
                long gapSeconds = ChronoUnit.SECONDS.between(lastHb, LocalDateTime.now());

                // 从 DB 取心跳间隔
                LicenseKey card = licenseKeyMapper.selectById(cardId);
                int interval = (card != null && card.getHeartbeatInterval() != null)
                        ? card.getHeartbeatInterval() : DEFAULT_HEARTBEAT_INTERVAL;

                if (gapSeconds > (long) interval * TIMEOUT_MULTIPLIER) {
                    timeoutCardIds.add(cardId);
                }
            } catch (Exception e) {
                log.debug("检查心跳超时异常: key={}", key, e);
            }
        }

        if (timeoutCardIds.isEmpty()) return;

        // 批量标记离线
        int offlineCount = 0;
        for (Long cardId : timeoutCardIds) {
            try {
                markOffline(cardId);
                offlineCount++;
            } catch (Exception e) {
                log.error("标记离线失败: cardId={}", cardId, e);
            }
        }

        log.info("心跳超时离线: 共{}张卡密", offlineCount);
    }

    private void markOffline(Long cardId) {
        // 更新 DB
        LicenseKey card = licenseKeyMapper.selectById(cardId);
        if (card != null && Boolean.TRUE.equals(card.getIsOnline())) {
            card.setIsOnline(false);
            card.setUpdatedAt(LocalDateTime.now());
            licenseKeyMapper.updateById(card);
        }

        // 清理 Redis
        String cardKey = HB_CARD_PREFIX + cardId;
        String json = redisTemplate.opsForValue().get(cardKey);
        if (json != null) {
            try {
                JSONObject payload = JSON.parseObject(json);
                String token = payload.getString("token");
                if (token != null) {
                    redisTemplate.delete(HB_TOKEN_PREFIX + token);
                }
            } catch (Exception ignored) {
            }
        }
        redisTemplate.delete(cardKey);
    }

    private Long parseCardId(String key) {
        try {
            return Long.parseLong(key.substring(HB_CARD_PREFIX.length()));
        } catch (Exception e) {
            return null;
        }
    }
}
