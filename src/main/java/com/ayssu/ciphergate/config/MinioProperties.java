package com.ayssu.ciphergate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

    private boolean enabled = false;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket = "ciphergate-plugins";
}
