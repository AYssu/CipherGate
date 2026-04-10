package com.ayssu.ciphergate.thirdparty.ws.service;

import cn.hutool.crypto.digest.BCrypt;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AppUserWsAuthService {
    private final ApplicationMapper applicationMapper;
    private final AppUserMapper appUserMapper;

    public AppUserWsAuthService(ApplicationMapper applicationMapper, AppUserMapper appUserMapper) {
        this.applicationMapper = applicationMapper;
        this.appUserMapper = appUserMapper;
    }

    public Application requireActiveAppByKey(String appKey) {
        if (!StringUtils.hasText(appKey)) {
            throw new RuntimeException("appKey required");
        }
        Application app = applicationMapper.selectOne(new LambdaQueryWrapper<Application>()
                .eq(Application::getAppKey, appKey.trim())
                .eq(Application::getDeleted, 0)
                .last("limit 1"));
        if (app == null) {
            throw new RuntimeException("app not found");
        }
        if (app.getStatus() != null && app.getStatus() != 1) {
            throw new RuntimeException("app disabled");
        }
        return app;
    }

    public AppUser loginAppUser(Long appId, String username, String password) {
        if (appId == null) {
            throw new RuntimeException("appId required");
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new RuntimeException("username/password required");
        }
        AppUser u = appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, appId)
                .eq(AppUser::getUsername, username.trim())
                .eq(AppUser::getDeleted, 0)
                .last("limit 1"));
        if (u == null) {
            throw new RuntimeException("bad credentials");
        }
        if (!BCrypt.checkpw(password, u.getPassword())) {
            throw new RuntimeException("bad credentials");
        }
        return u;
    }
}

