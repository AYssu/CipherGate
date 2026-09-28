package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.entity.FunctionPluginAppAccess;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.FunctionPluginAppAccessMapper;
import com.ayssu.ciphergate.service.FunctionPluginAppAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FunctionPluginAppAccessServiceImpl implements FunctionPluginAppAccessService {

    private final FunctionPluginAppAccessMapper accessMapper;
    private final ApplicationMapper applicationMapper;

    @Override
    public boolean isAllowed(String pluginId, Long appId) {
        if (!StringUtils.hasText(pluginId) || appId == null) {
            return false;
        }
        return accessMapper.selectAppIdsByPluginId(pluginId.trim()).contains(appId);
    }

    @Override
    public List<Long> getAllowedAppIds(String pluginId) {
        if (!StringUtils.hasText(pluginId)) {
            return List.of();
        }
        return List.copyOf(accessMapper.selectAppIdsByPluginId(pluginId.trim()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceAllowedAppIds(String pluginId, List<Long> appIds) {
        if (!StringUtils.hasText(pluginId)) {
            throw new IllegalArgumentException("pluginId 不能为空");
        }

        Set<Long> normalized = new LinkedHashSet<>();
        if (appIds != null) {
            for (Long appId : appIds) {
                if (appId == null) {
                    continue;
                }
                Application application = applicationMapper.selectById(appId);
                if (application == null || Integer.valueOf(1).equals(application.getDeleted())) {
                    throw new IllegalArgumentException("应用不存在: " + appId);
                }
                normalized.add(appId);
            }
        }

        String normalizedPluginId = pluginId.trim();
        accessMapper.deleteByPluginId(normalizedPluginId);
        for (Long appId : normalized) {
            FunctionPluginAppAccess row = new FunctionPluginAppAccess();
            row.setPluginId(normalizedPluginId);
            row.setAppId(appId);
            row.setCreatedAt(LocalDateTime.now());
            accessMapper.insert(row);
        }
    }
}
