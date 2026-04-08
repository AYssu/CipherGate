package com.ayssu.ciphergate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.plugin")
public class PluginProperties {

    /**
     * 本地临时缓存目录，仅用于运行时加载插件。
     */
    private String tempDir = System.getProperty("java.io.tmpdir") + "/ciphergate-plugins";
}
