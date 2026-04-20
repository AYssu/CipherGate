package com.ayssu.ciphergate.thirdparty.ws.service;

import cn.hutool.crypto.digest.BCrypt;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

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

    /**
     * WS 登录：identifier 支持「用户名」或「邮箱」。
     * 终端协议字段名仍为 username（兼容旧客户端）。
     */
    public AppUser loginAppUser(Long appId, String identifier, String password) {
        if (appId == null) {
            throw new RuntimeException("appId required");
        }
        if (!StringUtils.hasText(identifier) || !StringUtils.hasText(password)) {
            throw new RuntimeException("username/password required");
        }

        String raw = identifier.trim();
        LambdaQueryWrapper<AppUser> q = new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, appId)
                .eq(AppUser::getDeleted, 0)
                .and(w -> w.eq(AppUser::getUsername, raw)
                        .or()
                        // email 支持忽略大小写匹配
                        .apply("lower(email) = lower({0})", raw))
                .last("limit 2");

        List<AppUser> users = appUserMapper.selectList(q);
        if (users == null || users.isEmpty()) {
            throw new RuntimeException("bad credentials");
        }
        if (users.size() > 1) {
            throw new RuntimeException("ambiguous identifier");
        }
        AppUser u = users.get(0);
        if (!BCrypt.checkpw(password, u.getPassword())) {
            throw new RuntimeException("bad credentials");
        }
        return u;
    }
}

