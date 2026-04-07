package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicClientRegistrationRepository implements ClientRegistrationRepository {
    
    private final SystemConfigService systemConfigService;
    
    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        if ("github".equals(registrationId)) {
            return createGitHubClientRegistration();
        }
        return null;
    }
    
    private ClientRegistration createGitHubClientRegistration() {
        try {
            String clientId = systemConfigService.getGithubClientId();
            String clientSecret = systemConfigService.getGithubClientSecret();
            String redirectUri = systemConfigService.getGithubRedirectUri();
            
            log.info("创建动态 GitHub OAuth2 客户端注册");
            log.info("Client ID: {}", clientId);
            log.info("Redirect URI: {}", redirectUri);
            
            return ClientRegistration.withRegistrationId("github")
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(redirectUri)
                    .scope("user:email", "read:user")
                    .authorizationUri("https://github.com/login/oauth/authorize")
                    .tokenUri("https://github.com/login/oauth/access_token")
                    .userInfoUri("https://api.github.com/user")
                    .userNameAttributeName("id")
                    .clientName("GitHub")
                    .build();
        } catch (Exception e) {
            log.error("创建 GitHub 客户端注册失败: {}", e.getMessage(), e);
            // 返回默认配置作为后备
            return createDefaultGitHubClientRegistration();
        }
    }
    
    private ClientRegistration createDefaultGitHubClientRegistration() {
        return ClientRegistration.withRegistrationId("github")
                .clientId("client_id")
                .clientSecret("client_normal_secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("user:email", "read:user")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
    }
}