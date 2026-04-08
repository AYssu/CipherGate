package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.config.DynamicClientRegistrationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/oauth2/test")
@RequiredArgsConstructor
@Tag(name = "OAuth2测试", description = "OAuth2动态配置验证接口")
public class OAuth2TestController {
    
    private final DynamicClientRegistrationRepository clientRegistrationRepository;
    
    @GetMapping("/github-config")
    @Operation(summary = "测试GitHub OAuth2配置")
    public Result<Map<String, Object>> testGitHubConfig() {
        try {
            ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("github");
            
            if (registration == null) {
                return Result.error("GitHub ClientRegistration 未找到");
            }
            
            return Result.success(Map.of(
                "clientId", registration.getClientId(),
                "clientSecretLength", registration.getClientSecret().length(),
                "clientSecretPrefix", registration.getClientSecret().substring(0, Math.min(4, registration.getClientSecret().length())),
                "redirectUri", registration.getRedirectUri(),
                "authorizationUri", registration.getProviderDetails().getAuthorizationUri(),
                "tokenUri", registration.getProviderDetails().getTokenUri(),
                "scopes", registration.getScopes()
            ));
        } catch (Exception e) {
            log.error("测试 GitHub 配置失败", e);
            return Result.error("测试失败: " + e.getMessage());
        }
    }
}
