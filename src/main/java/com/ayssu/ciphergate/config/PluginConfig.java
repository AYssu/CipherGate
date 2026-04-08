package com.ayssu.ciphergate.config;

import io.minio.MinioClient;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class PluginConfig {

    @Bean
    public PluginManager pluginManager(PluginProperties pluginProperties) {
        Path pluginsRoot = Paths.get(pluginProperties.getTempDir()).toAbsolutePath().normalize();
        return new DefaultPluginManager(pluginsRoot);
    }

    @Bean
    public MinioClient minioClient(MinioProperties minioProperties) {
        if (!minioProperties.isEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(minioProperties.getEndpoint())
                || !StringUtils.hasText(minioProperties.getAccessKey())
                || !StringUtils.hasText(minioProperties.getSecretKey())) {
            throw new IllegalStateException("MinIO 已启用但配置不完整");
        }
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }
}
