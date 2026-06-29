package com.ayssu.ciphergate.thirdparty.crypto;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 确保 AesDefaultCryptoPlugin 作为 Spring Bean 注册。
 */
@Configuration
public class CryptoPluginConfig {

    @Bean
    public AesDefaultCryptoPlugin aesDefaultCryptoPlugin() {
        return new AesDefaultCryptoPlugin();
    }
}
