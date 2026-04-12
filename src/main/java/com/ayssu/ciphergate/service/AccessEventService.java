package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.constant.AccessEventTypes;
import com.ayssu.ciphergate.entity.AccessEvent;
import com.ayssu.ciphergate.mapper.AccessEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 业务侧访问事件落库（失败不影响主流程）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessEventService {

    private final AccessEventMapper accessEventMapper;

    public void recordCardLogin(Long appId, Long licenseKeyId) {
        insert(AccessEventTypes.CARD_LOGIN, appId, licenseKeyId);
    }

    public void recordAppUserWsLogin(Long appId, Long appUserId) {
        insert(AccessEventTypes.APP_USER_WS_LOGIN, appId, appUserId);
    }

    private void insert(String type, Long appId, Long refId) {
        if (appId == null || refId == null || type == null) {
            return;
        }
        try {
            AccessEvent row = new AccessEvent();
            row.setEventType(type);
            row.setAppId(appId);
            row.setRefId(refId);
            row.setCreatedAt(LocalDateTime.now());
            accessEventMapper.insert(row);
        } catch (Exception e) {
            log.warn("access_event 写入失败 type={} appId={} refId={}: {}", type, appId, refId, e.getMessage());
        }
    }
}
