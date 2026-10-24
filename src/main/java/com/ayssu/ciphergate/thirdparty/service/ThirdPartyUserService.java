package com.ayssu.ciphergate.thirdparty.service;

import com.alibaba.fastjson2.JSON;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.AppUserBinding;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppUserBindingMapper;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.AccessEventService;
import com.ayssu.ciphergate.thirdparty.dto.UserLoginRequest;
import com.ayssu.ciphergate.thirdparty.dto.UserLoginResponse;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsAuthService;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsDeviceBindService;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsLoginRecorder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ThirdPartyUserService {
    private final ApplicationMapper applicationMapper;
    private final AppUserMapper appUserMapper;
    private final AppUserBindingMapper bindingMapper;
    private final AppUserWsAuthService authService;
    private final AppUserWsDeviceBindService deviceBindService;
    private final AppUserWsLoginRecorder loginRecorder;
    private final ThirdPartyAppVariableService variableService;
    private final AccessEventService accessEventService;

    @Transactional(rollbackFor = Exception.class)
    public UserLoginResponse login(Long appId, UserLoginRequest request, String clientIp) {
        validate(request);
        Application app = appId == null ? null : applicationMapper.selectById(appId);
        if (app == null || Integer.valueOf(1).equals(app.getDeleted())
                || (app.getStatus() != null && app.getStatus() != 1)) {
            throw new ThirdPartyUserLoginException(403, "应用不存在或已停用");
        }
        AppUser user;
        try {
            user = authService.loginAppUser(appId, request.getUsername(), request.getPassword());
        } catch (IllegalArgumentException e) {
            // 用户不存在、跨应用、密码错误和歧义账号统一反馈，避免泄露账号存在性。
            throw new ThirdPartyUserLoginException(401, "用户名或密码错误");
        }
        if (user == null || !appId.equals(user.getAppId()) || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new ThirdPartyUserLoginException(401, "用户名或密码错误");
        }
        boolean memberRequired = !Integer.valueOf(2).equals(app.getBusinessModel());
        if (memberRequired && (user.getMemberExpiresAt() == null
                || !user.getMemberExpiresAt().isAfter(LocalDateTime.now()))) {
            throw new ThirdPartyUserLoginException(403, "MEMBER_EXPIRED");
        }

        String deviceId = request.getDeviceId().trim();
        String deviceName = trimmed(request.getDeviceName());
        String deviceOs = trimmed(request.getDeviceOs());
        try {
            // 复用已有 ACCOUNT 绑定及软删除恢复规则，不创建或踢出任何 WS 会话。
            deviceBindService.bindOrTouchOnWsLogin(appId, user.getId(), deviceId, deviceName, deviceOs, clientIp);
        } catch (IllegalStateException e) {
            if ("DEVICE_BANNED".equals(e.getMessage()) || "DEVICE_CONFLICT".equals(e.getMessage())) {
                throw new ThirdPartyUserLoginException(403, e.getMessage());
            }
            throw e;
        }
        loginRecorder.recordSuccessfulHttpLogin(appId, user.getId(), clientIp, deviceId);
        // 登录计数采用数据库原子递增，读取本事务更新后的快照，避免并发登录丢计数。
        user = appUserMapper.selectById(user.getId());
        if (user == null || !appId.equals(user.getAppId()) || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new IllegalStateException("User disappeared during login");
        }
        if (memberRequired && (user.getMemberExpiresAt() == null
                || !user.getMemberExpiresAt().isAfter(LocalDateTime.now()))) {
            throw new ThirdPartyUserLoginException(403, "MEMBER_EXPIRED");
        }

        AppVariableTemplateContext context = new AppVariableTemplateContext();
        context.setAppId(appId);
        context.setAppKey(app.getAppKey());
        context.setUserId(user.getId());
        context.setUsername(user.getUsername());
        context.setMemberExpiresAt(user.getMemberExpiresAt());
        context.setClientIp(clientIp);
        context.setDeviceId(deviceId);
        context.setDeviceName(deviceName);
        context.setDeviceOs(deviceOs);
        context.setUserLoginCount(user.getLoginCount());
        context.setUserLastLoginAt(user.getLastLoginAt());
        context.setUserLastLoginIp(user.getLastLoginIp());

        UserLoginResponse response = new UserLoginResponse();
        response.setAppId(appId);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setMemberRequired(memberRequired);
        response.setExpiresAt(user.getMemberExpiresAt());
        response.setAvailable(memberRequired
                ? Math.max(0, Duration.between(LocalDateTime.now(), user.getMemberExpiresAt()).getSeconds()) : null);
        response.setLoginCount(user.getLoginCount());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setBindingCount(bindingMapper.selectCount(new LambdaQueryWrapper<AppUserBinding>()
                .eq(AppUserBinding::getAppId, appId).eq(AppUserBinding::getUserId, user.getId())
                .eq(AppUserBinding::getDeleted, 0)));
        response.setVariables(JSON.toJSONString(variableService.getEnabledVariablesMap(appId, context)));
        // 成功 HTTP 登录即为近期活跃；不创建 WS 会话或心跳 token。
        response.setOnline(true);
        response.setToken(null);
        AccessEventService.LoginIdentity identity = accessEventService.recordAppUserHttpLogin(
                appId, user.getId(), deviceId, clientIp);
        response.setIdentityType(identity.identityType());
        response.setIdentityId(identity.identityId());
        return response;
    }

    private static void validate(UserLoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername())
                || request.getUsername().trim().length() > 255
                || !StringUtils.hasText(request.getPassword()) || request.getPassword().length() > 256
                || !StringUtils.hasText(request.getDeviceId()) || request.getDeviceId().trim().length() > 255
                || trimmed(request.getDeviceName()).length() > 100 || trimmed(request.getDeviceOs()).length() > 50) {
            throw new ThirdPartyUserLoginException(400, "username、password、deviceId 必填，且字段不能超过长度限制");
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
