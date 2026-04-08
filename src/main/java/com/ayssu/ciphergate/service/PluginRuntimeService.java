package com.ayssu.ciphergate.service;

import java.util.Map;

public interface PluginRuntimeService {
    Map<String, String> generateKeys(String pluginId);
}
