package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.config.DynamicClientRegistrationRepository;
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
public class OAuth2TestController {
    
    private final DynamicClientRegistrationRepository clientRegistrationRepository;
    
    @GetMapping("/github-config")
    public Map<String, Object> testGitHubConfig() {
        try {
            ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("github");
            
            if (registration == null) {
                return Map.of(
                    "success", false,
                    "message", "GitHub ClientRegistration 未找到"
                );
            }
            
            return Map.of(
                "success", true,
                "clientId", registration.getClientId(),
                "clientSecretLength", registration.getClientSecret().length(),
                "clientSecretPrefix", registration.getClientSecret().substring(0, Math.min(4, registration.getClientSecret().length())),
                "redirectUri", registration.getRedirectUri(),
                "authorizationUri", registration.getProviderDetails().getAuthorizationUri(),
                "tokenUri", registration.getProviderDetails().getTokenUri(),
                "scopes", registration.getScopes()
            );
        } catch (Exception e) {
            log.error("测试 GitHub 配置失败", e);
            return Map.of(
                "success", false,
                "message", "测试失败: " + e.getMessage()
            );
        }
    }
}
