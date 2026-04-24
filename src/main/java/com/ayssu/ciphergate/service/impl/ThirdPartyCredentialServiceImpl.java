package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.dto.ThirdPartyCredentialDTO;
import com.ayssu.ciphergate.dto.ThirdPartyCredentialQueryDTO;
import com.ayssu.ciphergate.dto.ThirdPartyRechargeLogQueryDTO;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.ThirdPartyCredential;
import com.ayssu.ciphergate.entity.ThirdPartyRechargeLog;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.ThirdPartyCredentialMapper;
import com.ayssu.ciphergate.mapper.ThirdPartyRechargeLogMapper;
import com.ayssu.ciphergate.service.ThirdPartyCredentialService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ThirdPartyCredentialServiceImpl implements ThirdPartyCredentialService {

    private static final String ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final ThirdPartyCredentialMapper credentialMapper;
    private final ThirdPartyRechargeLogMapper rechargeLogMapper;
    private final ApplicationMapper applicationMapper;
    private final SecurityUtils securityUtils;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public Page<ThirdPartyCredential> pageCredentials(ThirdPartyCredentialQueryDTO queryDTO, Long operatorId) {
        Page<ThirdPartyCredential> page = new Page<>(
                queryDTO.getCurrent() == null ? 1 : queryDTO.getCurrent(),
                queryDTO.getSize() == null ? 10 : queryDTO.getSize()
        );
        LambdaQueryWrapper<ThirdPartyCredential> qw = new LambdaQueryWrapper<ThirdPartyCredential>()
                .eq(ThirdPartyCredential::getDeleted, 0)
                .like(StringUtils.hasText(queryDTO.getName()), ThirdPartyCredential::getName, queryDTO.getName())
                .like(StringUtils.hasText(queryDTO.getApiKey()), ThirdPartyCredential::getApiKey, queryDTO.getApiKey())
                .eq(queryDTO.getStatus() != null, ThirdPartyCredential::getStatus, queryDTO.getStatus())
                .orderByDesc(ThirdPartyCredential::getCreatedAt);
        if (queryDTO.getAppId() != null) {
            ensureAppPermission(queryDTO.getAppId(), operatorId);
            qw.eq(ThirdPartyCredential::getAppId, queryDTO.getAppId());
        } else if (!securityUtils.isAdmin(operatorId)) {
            List<Long> appIds = listOwnedAppIds(operatorId);
            if (appIds.isEmpty()) {
                qw.apply("1=0");
            } else {
                qw.in(ThirdPartyCredential::getAppId, appIds);
            }
        }
        Page<ThirdPartyCredential> out = credentialMapper.selectPage(page, qw);
        if (out.getRecords() != null) {
            for (ThirdPartyCredential record : out.getRecords()) {
                maskSecret(record);
            }
        }
        return out;
    }

    @Override
    public ThirdPartyCredential getCredential(Long id, Long operatorId) {
        ThirdPartyCredential row = credentialMapper.selectById(id);
        if (row == null || row.getDeleted() != null && row.getDeleted() == 1) {
            throw new RuntimeException("凭证不存在");
        }
        ensureAppPermission(row.getAppId(), operatorId);
        maskSecret(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThirdPartyCredential createCredential(ThirdPartyCredentialDTO dto, Long operatorId) {
        if (dto.getAppId() == null) {
            throw new RuntimeException("appId不能为空");
        }
        if (!StringUtils.hasText(dto.getName())) {
            throw new RuntimeException("凭证名称不能为空");
        }
        ensureAppPermission(dto.getAppId(), operatorId);
        ThirdPartyCredential row = new ThirdPartyCredential();
        row.setAppId(dto.getAppId());
        row.setName(dto.getName().trim());
        row.setApiKey("tpk_" + randomText(24));
        row.setApiSecret("tps_" + randomText(32));
        row.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        row.setAllowedIps(normalizeIps(dto.getAllowedIps()));
        row.setDailyLimit(dto.getDailyLimit());
        row.setTotalCallLimit(dto.getTotalCallLimit());
        row.setTotalDaysLimit(dto.getTotalDaysLimit());
        row.setUsedCallCount(0L);
        row.setUsedDaysCount(0L);
        row.setExpiresAt(dto.getExpiresAt());
        row.setRemark(dto.getRemark());
        row.setCreatedBy(operatorId);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        row.setDeleted(0);
        credentialMapper.insert(row);
        maskSecret(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThirdPartyCredential updateCredential(Long id, ThirdPartyCredentialDTO dto, Long operatorId) {
        ThirdPartyCredential row = getCredential(id, operatorId);
        if (StringUtils.hasText(dto.getName())) {
            row.setName(dto.getName().trim());
        }
        if (dto.getStatus() != null) {
            row.setStatus(dto.getStatus());
        }
        row.setAllowedIps(normalizeIps(dto.getAllowedIps()));
        row.setDailyLimit(dto.getDailyLimit());
        row.setTotalCallLimit(dto.getTotalCallLimit());
        row.setTotalDaysLimit(dto.getTotalDaysLimit());
        row.setExpiresAt(dto.getExpiresAt());
        row.setRemark(dto.getRemark());
        row.setUpdatedAt(LocalDateTime.now());
        credentialMapper.updateById(row);
        maskSecret(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCredential(Long id, Long operatorId) {
        ThirdPartyCredential row = getCredential(id, operatorId);
        row.setDeleted(1);
        row.setUpdatedAt(LocalDateTime.now());
        credentialMapper.updateById(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThirdPartyCredential rotateSecret(Long id, Long operatorId) {
        ThirdPartyCredential row = getCredential(id, operatorId);
        row.setApiSecret("tps_" + randomText(32));
        row.setUpdatedAt(LocalDateTime.now());
        credentialMapper.updateById(row);
        return row;
    }

    @Override
    public Page<ThirdPartyRechargeLog> pageRechargeLogs(ThirdPartyRechargeLogQueryDTO queryDTO, Long operatorId) {
        Page<ThirdPartyRechargeLog> page = new Page<>(
                queryDTO.getCurrent() == null ? 1 : queryDTO.getCurrent(),
                queryDTO.getSize() == null ? 10 : queryDTO.getSize()
        );
        String requestIp = trimToNull(queryDTO.getRequestIp());
        String outTradeNo = trimToNull(queryDTO.getOutTradeNo());
        String userEmail = trimToNull(queryDTO.getUserEmail());
        LambdaQueryWrapper<ThirdPartyRechargeLog> qw = new LambdaQueryWrapper<ThirdPartyRechargeLog>()
                .eq(queryDTO.getCredentialId() != null, ThirdPartyRechargeLog::getCredentialId, queryDTO.getCredentialId())
                .eq(queryDTO.getStatus() != null, ThirdPartyRechargeLog::getStatus, queryDTO.getStatus())
                .eq(StringUtils.hasText(requestIp), ThirdPartyRechargeLog::getRequestIp, requestIp)
                .eq(StringUtils.hasText(outTradeNo), ThirdPartyRechargeLog::getOutTradeNo, outTradeNo)
                .like(StringUtils.hasText(userEmail), ThirdPartyRechargeLog::getUserEmail, userEmail)
                .ge(queryDTO.getStartTime() != null, ThirdPartyRechargeLog::getCreatedAt, queryDTO.getStartTime())
                .le(queryDTO.getEndTime() != null, ThirdPartyRechargeLog::getCreatedAt, queryDTO.getEndTime())
                .orderByDesc(ThirdPartyRechargeLog::getCreatedAt);
        if (queryDTO.getAppId() != null) {
            ensureAppPermission(queryDTO.getAppId(), operatorId);
            qw.eq(ThirdPartyRechargeLog::getAppId, queryDTO.getAppId());
        } else if (!securityUtils.isAdmin(operatorId)) {
            List<Long> appIds = listOwnedAppIds(operatorId);
            if (appIds.isEmpty()) {
                qw.apply("1=0");
            } else {
                qw.in(ThirdPartyRechargeLog::getAppId, appIds);
            }
        }
        return rechargeLogMapper.selectPage(page, qw);
    }

    private void ensureAppPermission(Long appId, Long operatorId) {
        if (appId == null) {
            throw new RuntimeException("应用不存在");
        }
        Application app = applicationMapper.selectById(appId);
        if (app == null || (app.getDeleted() != null && app.getDeleted() == 1)) {
            throw new RuntimeException("应用不存在");
        }
        if (!securityUtils.isAdmin(operatorId) && !operatorId.equals(app.getOwnerId())) {
            throw new RuntimeException("无权限操作该应用");
        }
    }

    private List<Long> listOwnedAppIds(Long ownerId) {
        List<Application> apps = applicationMapper.selectList(new LambdaQueryWrapper<Application>()
                .eq(Application::getDeleted, 0)
                .eq(Application::getOwnerId, ownerId));
        List<Long> ids = new ArrayList<>();
        for (Application app : apps) {
            ids.add(app.getId());
        }
        return ids;
    }

    private String normalizeIps(String ips) {
        if (!StringUtils.hasText(ips)) {
            return null;
        }
        String[] arr = ips.split(",");
        List<String> out = new ArrayList<>();
        for (String ip : arr) {
            String v = ip == null ? "" : ip.trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out.isEmpty() ? null : String.join(",", out);
    }

    private String randomText(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHA.charAt(secureRandom.nextInt(ALPHA.length())));
        }
        return sb.toString();
    }

    private void maskSecret(ThirdPartyCredential row) {
        if (row != null) {
            row.setApiSecret(null);
        }
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
