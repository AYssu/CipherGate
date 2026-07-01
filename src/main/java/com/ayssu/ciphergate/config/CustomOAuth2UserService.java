package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.entity.Role;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;
    private final OAuth2ProxyConfig oAuth2ProxyConfig;

    public void configureRestOperations() {
        RestTemplate restTemplate = oAuth2ProxyConfig.createOAuth2RestTemplate();
        setRestOperations(restTemplate);
        log.info("CustomOAuth2UserService RestOperations configured with proxy routing");
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        User user = userService.findOrCreateUser(oauth2User);

        User userWithRoles = userService.getUserWithRolesAndPermissions(user.getId());
        if (userWithRoles.getStatus() == null || userWithRoles.getStatus() != 1) {
            throw new OAuth2AuthenticationException(new OAuth2Error("user_disabled"), "账号已被禁用");
        }

        Set<GrantedAuthority> authorities = new HashSet<>();

        if (userWithRoles.getRoles() != null && !userWithRoles.getRoles().isEmpty()) {
            for (Role role : userWithRoles.getRoles()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()));
                log.info("为用户 {} 添加角色权限: ROLE_{}", userWithRoles.getLogin(), role.getRoleCode());
            }
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            log.warn("用户 {} 没有角色，添加默认角色: ROLE_USER", userWithRoles.getLogin());
        }

        return new DefaultOAuth2User(
            authorities,
            oauth2User.getAttributes(),
            "login"
        );
    }
}
