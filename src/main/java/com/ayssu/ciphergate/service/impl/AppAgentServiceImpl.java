package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.dto.AppAgentDTO;
import com.ayssu.ciphergate.dto.AgentBindUserDTO;
import com.ayssu.ciphergate.entity.AppAgent;
import com.ayssu.ciphergate.entity.AppAgentPermission;
import com.ayssu.ciphergate.entity.AppAgentQuota;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.AppAgentMapper;
import com.ayssu.ciphergate.mapper.AppAgentPermissionMapper;
import com.ayssu.ciphergate.mapper.AppAgentQuotaMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.AppAgentService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppAgentServiceImpl implements AppAgentService {
    private final AppAgentMapper appAgentMapper;
    private final AppAgentPermissionMapper appAgentPermissionMapper;
    private final AppAgentQuotaMapper appAgentQuotaMapper;
    private final ApplicationMapper applicationMapper;
    private final UserMapper userMapper;
    private final SecurityUtils securityUtils;

    private void checkAppPermission(Long appId, Long operatorId) {
        Application app = applicationMapper.selectById(appId);
        if (app == null) {
            throw new RuntimeException("应用不存在");
        }
        if (!securityUtils.isAdmin(operatorId) && !operatorId.equals(app.getOwnerId())) {
            throw new RuntimeException("无权限操作该应用代理");
        }
    }

    @Override
    public List<AppAgentDTO> listByAppId(Long appId, Long operatorId) {
        checkAppPermission(appId, operatorId);
        List<AppAgent> rows = appAgentMapper.selectList(new LambdaQueryWrapper<AppAgent>()
                .eq(AppAgent::getAppId, appId)
                .eq(AppAgent::getDeleted, 0)
                .orderByDesc(AppAgent::getUpdatedAt));
        List<AppAgentDTO> out = new ArrayList<>();
        for (AppAgent a : rows) {
            out.add(toDto(a, true));
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppAgentDTO create(Long appId, AppAgentDTO dto, Long operatorId) {
        checkAppPermission(appId, operatorId);
        AppAgent row = new AppAgent();
        row.setAppId(appId);
        row.setAgentCode(StringUtils.hasText(dto.getAgentCode()) ? dto.getAgentCode().trim() : ("AGENT-" + System.currentTimeMillis()));
        row.setUserId(dto.getUserId());
        row.setScopeMode(StringUtils.hasText(dto.getScopeMode()) ? dto.getScopeMode().trim().toUpperCase() : "OWN_ONLY");
        row.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        row.setRemark(dto.getRemark());
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        row.setDeleted(0);
        appAgentMapper.insert(row);
        if (dto.getPermissions() != null) {
            updatePermissions(appId, row.getId(), dto.getPermissions(), operatorId);
        }
        if (dto.getQuotas() != null) {
            updateQuotas(appId, row.getId(), dto.getQuotas(), operatorId);
        }
        return toDto(appAgentMapper.selectById(row.getId()), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppAgentDTO update(Long appId, Long agentId, AppAgentDTO dto, Long operatorId) {
        checkAppPermission(appId, operatorId);
        AppAgent row = requireAgent(appId, agentId);
        if (StringUtils.hasText(dto.getAgentCode())) {
            row.setAgentCode(dto.getAgentCode().trim());
        }
        if (dto.getUserId() != null) {
            row.setUserId(dto.getUserId());
        }
        if (StringUtils.hasText(dto.getScopeMode())) {
            row.setScopeMode(dto.getScopeMode().trim().toUpperCase());
        }
        if (dto.getEnabled() != null) {
            row.setEnabled(dto.getEnabled());
        }
        if (dto.getRemark() != null) {
            row.setRemark(dto.getRemark());
        }
        row.setUpdatedAt(LocalDateTime.now());
        appAgentMapper.updateById(row);
        return toDto(appAgentMapper.selectById(agentId), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePermissions(Long appId, Long agentId, List<String> permissions, Long operatorId) {
        checkAppPermission(appId, operatorId);
        requireAgent(appId, agentId);
        appAgentPermissionMapper.delete(new LambdaQueryWrapper<AppAgentPermission>().eq(AppAgentPermission::getAgentId, agentId));
        if (permissions == null) {
            return;
        }
        for (String p : permissions) {
            if (!StringUtils.hasText(p)) {
                continue;
            }
            AppAgentPermission row = new AppAgentPermission();
            row.setAgentId(agentId);
            row.setPermissionCode(p.trim().toUpperCase());
            row.setCreatedAt(LocalDateTime.now());
            appAgentPermissionMapper.insert(row);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuotas(Long appId, Long agentId, Map<String, Long> quotas, Long operatorId) {
        checkAppPermission(appId, operatorId);
        requireAgent(appId, agentId);
        appAgentQuotaMapper.delete(new LambdaQueryWrapper<AppAgentQuota>().eq(AppAgentQuota::getAgentId, agentId));
        if (quotas == null) {
            return;
        }
        for (Map.Entry<String, Long> e : quotas.entrySet()) {
            if (!StringUtils.hasText(e.getKey())) {
                continue;
            }
            AppAgentQuota q = new AppAgentQuota();
            q.setAgentId(agentId);
            q.setKeyType(e.getKey().trim().toUpperCase());
            q.setQuotaTotal(Math.max(0L, e.getValue() == null ? 0L : e.getValue()));
            q.setQuotaUsed(0L);
            q.setCreatedAt(LocalDateTime.now());
            q.setUpdatedAt(LocalDateTime.now());
            appAgentQuotaMapper.insert(q);
        }
    }

    @Override
    public AgentBindUserDTO findBindUserByGithubId(Long appId, String githubId, Long operatorId) {
        checkAppPermission(appId, operatorId);
        if (!StringUtils.hasText(githubId)) {
            return null;
        }
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getGithubId, githubId.trim())
                .last("limit 1"));
        if (u == null) {
            return null;
        }
        AgentBindUserDTO dto = new AgentBindUserDTO();
        dto.setId(u.getId());
        dto.setGithubId(u.getGithubId());
        dto.setLogin(u.getLogin());
        dto.setName(u.getName());
        dto.setStatus(u.getStatus());
        return dto;
    }

    private AppAgent requireAgent(Long appId, Long agentId) {
        AppAgent row = appAgentMapper.selectById(agentId);
        if (row == null || !appId.equals(row.getAppId()) || (row.getDeleted() != null && row.getDeleted() == 1)) {
            throw new RuntimeException("代理不存在");
        }
        return row;
    }

    private AppAgentDTO toDto(AppAgent a, boolean withChildren) {
        AppAgentDTO dto = new AppAgentDTO();
        dto.setId(a.getId());
        dto.setAppId(a.getAppId());
        dto.setAgentCode(a.getAgentCode());
        dto.setUserId(a.getUserId());
        dto.setScopeMode(a.getScopeMode());
        dto.setEnabled(a.getEnabled());
        dto.setRemark(a.getRemark());
        if (!withChildren) {
            return dto;
        }
        List<AppAgentPermission> ps = appAgentPermissionMapper.selectList(new LambdaQueryWrapper<AppAgentPermission>()
                .eq(AppAgentPermission::getAgentId, a.getId()));
        dto.setPermissions(ps.stream().map(AppAgentPermission::getPermissionCode).toList());
        List<AppAgentQuota> qs = appAgentQuotaMapper.selectList(new LambdaQueryWrapper<AppAgentQuota>()
                .eq(AppAgentQuota::getAgentId, a.getId()));
        Map<String, Long> quotas = new LinkedHashMap<>();
        for (AppAgentQuota q : qs) {
            quotas.put(q.getKeyType(), q.getQuotaTotal());
        }
        dto.setQuotas(quotas);
        return dto;
    }
}

