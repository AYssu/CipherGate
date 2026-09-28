package com.ayssu.ciphergate.service;

import java.util.List;

/**
 * 函数插件应用授权服务。所有策略均为默认拒绝。
 */
public interface FunctionPluginAppAccessService {

    boolean isAllowed(String pluginId, Long appId);

    List<Long> getAllowedAppIds(String pluginId);

    void replaceAllowedAppIds(String pluginId, List<Long> appIds);
}
