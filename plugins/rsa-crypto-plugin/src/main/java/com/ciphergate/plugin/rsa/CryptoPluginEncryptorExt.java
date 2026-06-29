package com.ciphergate.plugin.rsa;

import com.ciphergate.plugin.api.CryptoPluginEncryptor;

/**
 * 继承公共 API 模块接口，PF4J 通过此发现插件。
 * 新插件只需依赖 ciphergate-plugin-api，无需依赖宿主源码。
 */
public interface CryptoPluginEncryptorExt extends CryptoPluginEncryptor {
}
