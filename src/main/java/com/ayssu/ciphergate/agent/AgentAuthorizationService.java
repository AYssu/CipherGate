package com.ayssu.ciphergate.agent;

import com.ayssu.ciphergate.entity.AppAgent;
import com.ayssu.ciphergate.entity.AppAgentPermission;
import com.ayssu.ciphergate.entity.AppAgentQuota;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppAgentMapper;
import com.ayssu.ciphergate.mapper.AppAgentPermissionMapper;
import com.ayssu.ciphergate.mapper.AppAgentQuotaMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentAuthorizationService {

    private final AppAgentMapper appAgentMapper;
    private final AppAgentPermissionMapper appAgentPermissionMapper;
    private final AppAgentQuotaMapper appAgentQuotaMapper;
    private final ApplicationMapper applicationMapper;

    public AppAgent findEnabledAgentForUser(Long appId, Long userId) {
        if (appId == null || userId == null) {
            return null;
        }
        AppAgent agent = appAgentMapper.selectOne(
                new LambdaQueryWrapper<AppAgent>()
                .eq(AppAgent::getAppId, appId)
                .eq(AppAgent::getUserId, userId)
                .eq(AppAgent::getDeleted, 0)
                .last("limit 1")
        );
        if (agent == null) {
            return null;
        }
        if (Boolean.TRUE.equals(agent.getEnabled())) {
            return agent;
        }
        return null;
    }

    public List<Long> listDelegatedAppIds(Long userId) {
        return listEnabledAgentsForUser(userId).stream().map(AppAgent::getAppId).distinct().toList();
    }

    public List<AppAgent> listEnabledAgentsForUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return appAgentMapper.selectList(
                        new LambdaQueryWrapper<AppAgent>()
                                .eq(AppAgent::getUserId, userId)
                                .eq(AppAgent::getEnabled, true)
                                .eq(AppAgent::getDeleted, 0));
    }

    public List<Long> listAccessibleAppIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Long> owned = applicationMapper.selectList(
                        new LambdaQueryWrapper<Application>()
                                .eq(Application::getOwnerId, userId)
                                .eq(Application::getDeleted, 0))
                .stream().map(Application::getId).toList();
        List<Long> delegated = listDelegatedAppIds(userId);
        List<Long> out = new ArrayList<>(owned);
        for (Long id : delegated) {
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    public Set<String> getAgentPermissions(Long agentId) {
        if (agentId == null) {
            return Set.of();
        }
        List<AppAgentPermission> rows = appAgentPermissionMapper.selectList(new LambdaQueryWrapper<AppAgentPermission>()
                .eq(AppAgentPermission::getAgentId, agentId));
        Set<String> out = new HashSet<>();
        for (AppAgentPermission r : rows) {
            if (r != null && StringUtils.hasText(r.getPermissionCode())) {
                out.add(r.getPermissionCode().trim().toUpperCase());
            }
        }
        return out;
    }

    public void requirePermission(Set<String> perms, String required, String message) {
        if (required == null) {
            return;
        }
        String k = required.trim().toUpperCase();
        if (perms == null || !perms.contains(k)) {
            throw new RuntimeException(message == null ? ("无代理权限: " + k) : message);
        }
    }

    public boolean isScopeAllInApp(AppAgent agent) {
        if (agent == null || !StringUtils.hasText(agent.getScopeMode())) {
            return false;
        }
        return "ALL_IN_APP".equalsIgnoreCase(agent.getScopeMode());
    }

    public boolean isOwner(Long appId, Long userId) {
        if (appId == null || userId == null) {
            return false;
        }
        Application app = applicationMapper.selectById(appId);
        return app != null && userId.equals(app.getOwnerId());
    }

    /**
     * 创建时扣减额度（不返还）。成功返回 true，否则抛错。
     */
    @Transactional(rollbackFor = Exception.class)
    public void consumeQuotaOrThrow(Long agentId, String keyType, long amount) {
        if (agentId == null) {
            throw new RuntimeException("代理额度校验失败");
        }
        if (!StringUtils.hasText(keyType)) {
            throw new RuntimeException("卡密类型缺失");
        }
        if (amount < 1) {
            return;
        }
        String kt = keyType.trim().toUpperCase();
        // 原子扣减：quota_used + amount <= quota_total
        LambdaUpdateWrapper<AppAgentQuota> up = new LambdaUpdateWrapper<>();
        up.eq(AppAgentQuota::getAgentId, agentId)
                .eq(AppAgentQuota::getKeyType, kt)
                .apply("quota_used + {0} <= quota_total", amount)
                .setSql("quota_used = quota_used + " + amount);
        int updated = appAgentQuotaMapper.update(null, up);
        if (updated > 0) {
            return;
        }
        // 没有额度行或不足
        AppAgentQuota row = appAgentQuotaMapper.selectOne(new LambdaQueryWrapper<AppAgentQuota>()
                .eq(AppAgentQuota::getAgentId, agentId)
                .eq(AppAgentQuota::getKeyType, kt)
                .last("limit 1"));
        if (row == null) {
            throw new RuntimeException("该卡密类型未配置代理额度: " + kt);
        }
        long total = row.getQuotaTotal() == null ? 0 : row.getQuotaTotal();
        long used = row.getQuotaUsed() == null ? 0 : row.getQuotaUsed();
        throw new RuntimeException("代理额度不足: " + kt + " (" + used + "/" + total + ")");
    }
}

