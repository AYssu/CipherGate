package com.ayssu.ciphergate.portal.service;

import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.CaptchaUtil;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.portal.dto.*;
import com.ayssu.ciphergate.portal.entity.PortalLoginLog;
import com.ayssu.ciphergate.portal.entity.PortalVerifyCode;
import com.ayssu.ciphergate.portal.mapper.PortalLoginLogMapper;
import com.ayssu.ciphergate.portal.mapper.PortalVerifyCodeMapper;
import com.ayssu.ciphergate.portal.util.PortalJwtUtil;
import com.ayssu.ciphergate.service.GeoIpService;
import com.ayssu.ciphergate.service.mail.SystemSmtpMailService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalAuthService {

    private final AppUserMapper appUserMapper;
    private final ApplicationMapper applicationMapper;
    private final PortalLoginLogMapper loginLogMapper;
    private final PortalVerifyCodeMapper verifyCodeMapper;
    private final PortalJwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final GeoIpService geoIpService;
    private final SystemSmtpMailService mailService;
    private final com.ayssu.ciphergate.service.PublicAbuseGuard abuseGuard;
    private final PortalOtpService otpService;
    private final PortalRecoveryDispatcher recoveryDispatcher;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String DUMMY_HASH = ENCODER.encode("dummy-password-for-timing-only");
    private static final String CAPTCHA_PREFIX = "portal:captcha:";

    public Map<String, Object> generateCaptcha(String clientIp) throws IOException {
        abuseGuard.check("portal-captcha", clientIp, 30, java.time.Duration.ofMinutes(5));
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 80);
        String code = captcha.getCode();
        String captchaId = UUID.randomUUID().toString().replace("-", "");

        redisTemplate.opsForValue().set(CAPTCHA_PREFIX + captchaId, code, 5, TimeUnit.MINUTES);

        // 将验证码图片转为base64返回
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        captcha.write(baos);
        String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());

        Map<String, Object> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("image", base64);
        return result;
    }

    public PortalLoginResponse login(PortalLoginRequest request, String clientIp) {
        request.setEmail(normalizeEmail(request.getEmail()));
        if (request.getPassword() == null || request.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("邮箱或密码错误");
        abuseGuard.check("portal-login-ip", clientIp, 60, java.time.Duration.ofMinutes(15));
        abuseGuard.check("portal-login-account", request.getEmail(), 20, java.time.Duration.ofMinutes(15));
        String cachedCode = redisTemplate.opsForValue().getAndDelete(CAPTCHA_PREFIX + request.getCaptchaId());
        if (cachedCode == null || !cachedCode.equalsIgnoreCase(request.getCaptchaCode())) {
            throw new IllegalArgumentException("验证码错误或已过期，请重新获取");
        }

        // 查询该邮箱绑定的所有应用用户
        List<AppUser> appUsers = appUserMapper.selectList(
            new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getEmail, request.getEmail())
                .eq(AppUser::getDeleted, 0)
        );

        if (appUsers.isEmpty()) {
            ENCODER.matches(request.getPassword(), DUMMY_HASH);
            throw new IllegalArgumentException("邮箱或密码错误");
        }

        // Each application has its own credential. Never grant sibling accounts just because email matches.
        appUsers = appUsers.stream().filter(user -> matchesPassword(request.getPassword(), user.getPassword()))
            .filter(this::activeApplication).toList();
        if (appUsers.isEmpty()) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }
        populateAppNames(appUsers);

        // 如果只有一个应用，直接返回 JWT
        if (appUsers.size() == 1) {
            AppUser user = appUsers.get(0);
            String token = jwtUtil.generateSessionToken(user);
            recordLoginLog(user.getId(), user.getAppId(), clientIp, "SUCCESS");

            List<PortalAppInfo> apps = List.of(PortalAppInfo.builder()
                .appId(user.getAppId())
                .appName(user.getAppName())
                .memberActive(user.getMemberActive())
                .memberExpiresAt(user.getMemberExpiresAt() != null ? user.getMemberExpiresAt().toString() : null)
                .build());

            return PortalLoginResponse.builder()
                .token(token)
                .apps(apps)
                .needSelectApp(false)
                .build();
        }

        // 多个应用，返回应用列表让用户选择
        List<PortalAppInfo> apps = new ArrayList<>();
        for (AppUser user : appUsers) {
            apps.add(PortalAppInfo.builder()
                .appId(user.getAppId())
                .appName(user.getAppName())
                .memberActive(user.getMemberActive())
                .memberExpiresAt(user.getMemberExpiresAt() != null ? user.getMemberExpiresAt().toString() : null)
                .build());
        }

        // Short selection ticket contains only accounts whose individual password matched.
        String tempToken = jwtUtil.generateSelectionToken(request.getEmail(), appUsers);

        return PortalLoginResponse.builder()
            .token(tempToken)
            .apps(apps)
            .needSelectApp(true)
            .build();
    }

    public String selectApp(Long appId, String email, Map<String, ?> grants, String clientIp) {
        AppUser appUser = appUserMapper.selectOne(
            new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, appId)
                .eq(AppUser::getEmail, email)
                .eq(AppUser::getDeleted, 0)
        );

        if (appUser == null || !activeApplication(appUser) || grants == null
                || !jwtUtil.credentialStamp(appUser).equals(grants.get(appUser.getId().toString()))) {
            throw new IllegalArgumentException("未验证该应用的登录凭据，请重新登录");
        }

        String token = jwtUtil.generateSessionToken(appUser);
        recordLoginLog(appUser.getId(), appUser.getAppId(), clientIp, "SUCCESS");
        return token;
    }

    public List<Map<String, Object>> getAppsByEmail(String email, String code, String clientIp) {
        email = normalizeEmail(email);
        abuseGuard.check("portal-recovery-verify-ip", clientIp, 30, java.time.Duration.ofMinutes(15));
        otpService.verify(email, "RECOVERY", code, false);
        List<AppUser> appUsers = appUserMapper.selectList(
            new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getEmail, email)
                .eq(AppUser::getDeleted, 0)
        );

        if (appUsers.isEmpty()) {
            throw new IllegalArgumentException("该邮箱未注册");
        }

        populateAppNames(appUsers);

        return appUsers.stream().map(u -> {
            Map<String, Object> item = new HashMap<>();
            item.put("appId", u.getAppId());
            item.put("appName", u.getAppName());
            return item;
        }).collect(Collectors.toList());
    }

    public void sendRecoveryCode(String email, String clientIp, String captchaId, String captchaCode) {
        email = normalizeEmail(email);
        abuseGuard.check("portal-mail-ip", clientIp, 10, java.time.Duration.ofHours(1));
        abuseGuard.check("all-mail", "global", 1000, java.time.Duration.ofHours(1));
        String expected = redisTemplate.opsForValue().getAndDelete(CAPTCHA_PREFIX + captchaId);
        if (expected == null || !expected.equalsIgnoreCase(captchaCode)) throw new IllegalArgumentException("验证码错误或已过期，请重新获取");
        otpService.claimSend(email, "RECOVERY");
        recoveryDispatcher.dispatch(email);
    }

    @org.springframework.transaction.annotation.Transactional
    public void resetPassword(PortalPasswordRecoveryRequest request, String clientIp) {
        request.setEmail(normalizeEmail(request.getEmail()));
        abuseGuard.check("portal-reset-ip", clientIp, 30, java.time.Duration.ofMinutes(15));
        abuseGuard.check("portal-reset-email", request.getEmail(), 12, java.time.Duration.ofMinutes(15));
        com.ayssu.ciphergate.util.PasswordPolicy.validate(request.getNewPassword());
        AppUser user = appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
            .eq(AppUser::getAppId, request.getAppId()).eq(AppUser::getEmail, request.getEmail()).eq(AppUser::getDeleted, 0).last("FOR UPDATE"));
        if (user == null) throw new IllegalArgumentException("验证码无效或已过期");
        otpService.verify(request.getEmail(), "RECOVERY", request.getVerifyCode(), true);
        int changed = appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>().eq(AppUser::getId, user.getId())
            .set(AppUser::getPassword, ENCODER.encode(request.getNewPassword())).set(AppUser::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) throw new IllegalStateException("密码更新失败，请重试");
    }

    public void sendEmailVerifyCode(Long userId, String email, String clientIp) {
        email = normalizeEmail(email);
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        abuseGuard.check("portal-mail-ip", clientIp, 10, java.time.Duration.ofHours(1));
        abuseGuard.check("portal-mail-user", userId.toString(), 10, java.time.Duration.ofHours(1));
        abuseGuard.check("all-mail", "global", 1000, java.time.Duration.ofHours(1));
        String purpose = PortalOtpService.emailChangePurpose(user);
        otpService.claimSend(email, purpose);
        otpService.send(email, purpose);
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean matchesPassword(String supplied, String stored) {
        try { return ENCODER.matches(supplied, stored == null ? DUMMY_HASH : stored) && stored != null; }
        catch (IllegalArgumentException invalidLegacyHash) { ENCODER.matches(supplied, DUMMY_HASH); return false; }
    }

    private boolean activeApplication(AppUser user) {
        Application app = applicationMapper.selectById(user.getAppId());
        return app != null && !Integer.valueOf(1).equals(app.getDeleted()) && !Integer.valueOf(1).equals(user.getDeleted()) && Integer.valueOf(1).equals(app.getStatus());
    }

    private void populateAppNames(List<AppUser> appUsers) {
        if (appUsers.isEmpty()) return;
        List<Long> appIds = appUsers.stream().map(AppUser::getAppId).collect(Collectors.toList());
        List<Application> apps = applicationMapper.selectBatchIds(appIds);
        Map<Long, String> nameMap = apps.stream().collect(Collectors.toMap(Application::getId, Application::getAppName));
        for (AppUser user : appUsers) {
            user.setAppName(nameMap.getOrDefault(user.getAppId(), "未知应用"));
        }
    }

    private void recordLoginLog(Long appUserId, Long appId, String clientIp, String status) {
        PortalLoginLog loginLog = new PortalLoginLog();
        loginLog.setAppUserId(appUserId);
        loginLog.setAppId(appId);
        loginLog.setLoginIp(clientIp);
        loginLog.setStatus(status);
        loginLog.setLoginType("PASSWORD");

        // 解析 IP 归属地
        if (StringUtils.hasText(clientIp) && geoIpService.isEnabled()) {
            geoIpService.resolve(clientIp).ifPresent(geo ->
                loginLog.setIpRegion(formatRegion(geo.country(), geo.province(), geo.city()))
            );
        }

        loginLogMapper.insert(loginLog);
    }

    private String formatRegion(String country, String province, String city) {
        String c = StringUtils.hasText(country) ? country.trim() : "";
        String p = StringUtils.hasText(province) ? province.trim() : "";
        String ci = StringUtils.hasText(city) ? city.trim() : "";
        StringBuilder sb = new StringBuilder();
        if (!c.isEmpty()) sb.append(c);
        if (!p.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(p);
        }
        if (!ci.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(ci);
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
