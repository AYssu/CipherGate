package com.ayssu.ciphergate.thirdparty.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 三方下载链接等对外 URL 构造（便于经网关/反向代理暴露统一域名）。
 */
@Data
@ConfigurationProperties(prefix = "app.third-party")
public class ThirdPartyPublicProperties {

    /**
     * 本服务对外的根 URL 前缀（无末尾 /），用于拼接 {@code /api/v1/app/update-package?ticket=}。
     * 例：https://api.example.com 或 https://gw.example.com/ciphergate
     * 留空则按当前 HTTP 请求的 Host/Scheme 生成（需代理正确转发 X-Forwarded-*）。
     */
    private String publicBaseUrl = "";
}
