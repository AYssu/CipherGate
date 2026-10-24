package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.dto.ThirdPartyCredentialDTO;
import com.ayssu.ciphergate.dto.ThirdPartyCredentialQueryDTO;
import com.ayssu.ciphergate.dto.ThirdPartyRechargeLogQueryDTO;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.ThirdPartyCredential;
import com.ayssu.ciphergate.entity.ThirdPartyRechargeLog;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.ThirdPartyCredentialMapper;
import com.ayssu.ciphergate.mapper.ThirdPartyCredentialAppMapper;
import com.ayssu.ciphergate.mapper.ThirdPartyRechargeLogMapper;
import com.ayssu.ciphergate.service.ThirdPartyCredentialService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ThirdPartyCredentialServiceImpl implements ThirdPartyCredentialService {

    private static final String ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final ThirdPartyCredentialMapper credentialMapper;
    private final ThirdPartyCredentialAppMapper credentialAppMapper;
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
            qw.apply("""
                (EXISTS (SELECT 1 FROM third_party_credential_app b
                    WHERE b.credential_id = third_party_credential.id AND b.app_id = {0})
                 OR (app_id = {0} AND NOT EXISTS (SELECT 1 FROM third_party_credential_app b
                    WHERE b.credential_id = third_party_credential.id)))
                """, queryDTO.getAppId());
        }
        // 共享凭证必须拥有所有绑定应用的权限，不能因拥有其中一个应用而泄露或重置整组密钥。
        if (!securityUtils.isAdmin(operatorId)) {
            qw.apply("""
                ((EXISTS (SELECT 1 FROM third_party_credential_app b WHERE b.credential_id = third_party_credential.id)
                  AND NOT EXISTS (SELECT 1 FROM third_party_credential_app b LEFT JOIN application a ON a.id = b.app_id
                    WHERE b.credential_id = third_party_credential.id
                      AND (a.id IS NULL OR a.deleted <> 0 OR a.owner_id IS NULL OR a.owner_id <> {0})))
                 OR (NOT EXISTS (SELECT 1 FROM third_party_credential_app b WHERE b.credential_id = third_party_credential.id)
                  AND EXISTS (SELECT 1 FROM application a WHERE a.id = third_party_credential.app_id
                    AND a.deleted = 0 AND a.owner_id = {0})))
                """, operatorId);
        }
        Page<ThirdPartyCredential> out = credentialMapper.selectPage(page, qw);
        if (out.getRecords() != null && !out.getRecords().isEmpty()) {
            Map<Long, List<Long>> bindings = credentialAppMapper.selectBindings(
                    out.getRecords().stream().map(ThirdPartyCredential::getId).toList()).stream()
                    .collect(Collectors.groupingBy(ThirdPartyCredentialAppMapper.Binding::getCredentialId,
                            Collectors.mapping(ThirdPartyCredentialAppMapper.Binding::getAppId, Collectors.toList())));
            for (ThirdPartyCredential record : out.getRecords()) {
                record.setAppIds(bindings.getOrDefault(record.getId(), List.of()));
                maskSecret(record);
            }
        }
        return out;
    }

    @Override
    public ThirdPartyCredential getCredential(Long id, Long operatorId) {
        ThirdPartyCredential row = loadAuthorizedCredential(id, operatorId, false);
        maskSecret(row);
        return row;
    }

    private ThirdPartyCredential loadAuthorizedCredential(Long id, Long operatorId, boolean lock) {
        ThirdPartyCredential row = lock ? credentialMapper.selectForUpdate(id) : credentialMapper.selectById(id);
        if (row == null || Integer.valueOf(1).equals(row.getDeleted())) {
            throw new RuntimeException("凭证不存在");
        }
        List<Long> appIds = credentialAppMapper.selectAppIds(id);
        if (appIds.isEmpty()) {
            throw new RuntimeException("凭证未绑定应用");
        }
        // 管理员可修复包含已删除应用的授权集合；普通所有者须拥有所有有效应用。
        if (!securityUtils.isAdmin(operatorId)) {
            for (Long appId : appIds) {
                ensureAppPermission(appId, operatorId);
            }
        }
        row.setAppIds(appIds);
        return row;
    }

    private List<Long> resolveAppIds(ThirdPartyCredentialDTO dto) {
        List<Long> ids = dto.getAppIds() != null ? dto.getAppIds()
                : dto.getAppId() == null ? List.of() : List.of(dto.getAppId());
        if (ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new RuntimeException("请选择1至100个有效应用");
        }
        if (dto.getAppIds() != null && dto.getAppId() != null && !ids.contains(dto.getAppId())) {
            throw new RuntimeException("appId必须属于appIds授权集合");
        }
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThirdPartyCredential createCredential(ThirdPartyCredentialDTO dto, Long operatorId) {
        List<Long> appIds = resolveAppIds(dto);
        if (!StringUtils.hasText(dto.getName())) {
            throw new RuntimeException("凭证名称不能为空");
        }
        for (Long appId : appIds) {
            ensureAppPermission(appId, operatorId);
        }
        ThirdPartyCredential row = new ThirdPartyCredential();
        row.setAppId(appIds.get(0));
        row.setAppIds(appIds);
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
        credentialAppMapper.insertBindings(row.getId(), appIds);
        // 新 Secret 仅在创建和主动轮换时返回一次；列表/详情/普通编辑不返回。

        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThirdPartyCredential updateCredential(Long id, ThirdPartyCredentialDTO dto, Long operatorId) {
        ThirdPartyCredential row = loadAuthorizedCredential(id, operatorId, true);
        // 不传绑定字段保持原授权。旧版表单只回传原 appId 时，不截断已配置的多应用集合。
        boolean replaceBindings = dto.getAppIds() != null
                || (dto.getAppId() != null && !dto.getAppId().equals(row.getAppId()));
        if (replaceBindings) {
            List<Long> appIds = resolveAppIds(dto);
            for (Long appId : appIds) {
                ensureAppPermission(appId, operatorId);
            }
            row.setAppId(appIds.get(0));
            row.setAppIds(appIds);
        }
        if (StringUtils.hasText(dto.getName())) row.setName(dto.getName().trim());
        if (dto.getStatus() != null) row.setStatus(dto.getStatus());
        row.setAllowedIps(normalizeIps(dto.getAllowedIps()));
        row.setDailyLimit(dto.getDailyLimit());
        row.setTotalCallLimit(dto.getTotalCallLimit());
        row.setTotalDaysLimit(dto.getTotalDaysLimit());
        row.setExpiresAt(dto.getExpiresAt());
        row.setRemark(dto.getRemark());
        row.setUpdatedAt(LocalDateTime.now());
        // 明确只写配置列：不把 Secret 或并发累加的消费计数写回，也允许清除限额/IP/到期时间。
        credentialMapper.update(null, new LambdaUpdateWrapper<ThirdPartyCredential>()
                .eq(ThirdPartyCredential::getId, id)
                .set(ThirdPartyCredential::getAppId, row.getAppId())
                .set(ThirdPartyCredential::getName, row.getName())
                .set(ThirdPartyCredential::getStatus, row.getStatus())
                .set(ThirdPartyCredential::getAllowedIps, row.getAllowedIps())
                .set(ThirdPartyCredential::getDailyLimit, row.getDailyLimit())
                .set(ThirdPartyCredential::getTotalCallLimit, row.getTotalCallLimit())
                .set(ThirdPartyCredential::getTotalDaysLimit, row.getTotalDaysLimit())
                .set(ThirdPartyCredential::getExpiresAt, row.getExpiresAt())
                .set(ThirdPartyCredential::getRemark, row.getRemark())
                .set(ThirdPartyCredential::getUpdatedAt, row.getUpdatedAt()));
        if (replaceBindings) {
            credentialAppMapper.deleteBindings(id);
            credentialAppMapper.insertBindings(id, row.getAppIds());
        }
        maskSecret(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCredential(Long id, Long operatorId) {
        loadAuthorizedCredential(id, operatorId, true);
        credentialMapper.deleteById(id);
        credentialAppMapper.deleteBindings(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThirdPartyCredential rotateSecret(Long id, Long operatorId) {
        ThirdPartyCredential row = loadAuthorizedCredential(id, operatorId, true);
        row.setApiSecret("tps_" + randomText(32));
        row.setUpdatedAt(LocalDateTime.now());
        credentialMapper.update(null, new LambdaUpdateWrapper<ThirdPartyCredential>()
                .eq(ThirdPartyCredential::getId, id)
                .set(ThirdPartyCredential::getApiSecret, row.getApiSecret())
                .set(ThirdPartyCredential::getUpdatedAt, row.getUpdatedAt()));
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
