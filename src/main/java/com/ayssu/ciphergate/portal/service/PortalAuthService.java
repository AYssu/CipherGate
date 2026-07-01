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

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String CAPTCHA_PREFIX = "portal:captcha:";
    private static final String LOGIN_FAIL_PREFIX = "portal:login:fail:";
    private static final String IP_FAIL_PREFIX = "portal:login:fail:ip:";
    private static final String CAPTCHA_RATE_PREFIX = "portal:captcha:rate:";

    public Map<String, Object> generateCaptcha() throws IOException {
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
        // 验证码校验
        String cachedCode = redisTemplate.opsForValue().get(CAPTCHA_PREFIX + request.getCaptchaId());
        if (cachedCode == null) {
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }
        if (!cachedCode.equalsIgnoreCase(request.getCaptchaCode())) {
            throw new IllegalArgumentException("验证码错误");
        }
        redisTemplate.delete(CAPTCHA_PREFIX + request.getCaptchaId());

        // IP 限流
        String ipKey = IP_FAIL_PREFIX + clientIp;
        String ipFailCount = redisTemplate.opsForValue().get(ipKey);
        if (ipFailCount != null && Integer.parseInt(ipFailCount) >= 10) {
            throw new IllegalArgumentException("登录尝试次数过多，请15分钟后重试");
        }

        // 账号限流
        String accountKey = LOGIN_FAIL_PREFIX + request.getEmail();
        String accountFailCount = redisTemplate.opsForValue().get(accountKey);
        if (accountFailCount != null && Integer.parseInt(accountFailCount) >= 5) {
            throw new IllegalArgumentException("该邮箱登录尝试次数过多，请15分钟后重试");
        }

        // 查询该邮箱绑定的所有应用用户
        List<AppUser> appUsers = appUserMapper.selectList(
            new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getEmail, request.getEmail())
                .eq(AppUser::getDeleted, 0)
        );

        if (appUsers.isEmpty()) {
            recordFailure(accountKey, ipKey);
            throw new IllegalArgumentException("邮箱或密码错误");
        }

        // 填充应用名称
        populateAppNames(appUsers);

        // 校验密码（使用第一个用户的密码）
        AppUser firstUser = appUsers.get(0);
        if (firstUser.getPassword() == null || !ENCODER.matches(request.getPassword(), firstUser.getPassword())) {
            recordFailure(accountKey, ipKey);
            throw new IllegalArgumentException("邮箱或密码错误");
        }

        // 清除失败记录
        redisTemplate.delete(accountKey);
        redisTemplate.delete(ipKey);

        // 如果只有一个应用，直接返回 JWT
        if (appUsers.size() == 1) {
            AppUser user = appUsers.get(0);
            String token = jwtUtil.generateToken(user.getId(), user.getAppId(), user.getEmail(), user.getNickname());
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

        // 生成一个临时 token（仅包含 email，不含 appId）
        String tempToken = jwtUtil.generateToken(appUsers.get(0).getId(), 0L, request.getEmail(), "");

        return PortalLoginResponse.builder()
            .token(tempToken)
            .apps(apps)
            .needSelectApp(true)
            .build();
    }

    public String selectApp(Long appId, String email, String clientIp) {
        AppUser appUser = appUserMapper.selectOne(
            new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, appId)
                .eq(AppUser::getEmail, email)
                .eq(AppUser::getDeleted, 0)
        );

        if (appUser == null) {
            throw new IllegalArgumentException("该应用无此用户");
        }

        String token = jwtUtil.generateToken(appUser.getId(), appUser.getAppId(), appUser.getEmail(), appUser.getNickname());
        recordLoginLog(appUser.getId(), appUser.getAppId(), clientIp, "SUCCESS");
        return token;
    }

    public List<Map<String, Object>> getAppsByEmail(String email) {
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

    public void sendRecoveryCode(String email, String clientIp) {
        List<AppUser> appUsers = appUserMapper.selectList(
            new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getEmail, email)
                .eq(AppUser::getDeleted, 0)
        );

        if (appUsers.isEmpty()) {
            throw new IllegalArgumentException("该邮箱未注册");
        }

        // 防刷：60秒内不能重复发送
        String rateKey = CAPTCHA_RATE_PREFIX + "recovery:" + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateKey))) {
            throw new IllegalStateException("发送过于频繁，请60秒后重试");
        }

        String code = String.valueOf((int) (Math.random() * 900000 + 100000));

        PortalVerifyCode verifyCode = new PortalVerifyCode();
        verifyCode.setEmail(email);
        verifyCode.setCode(code);
        verifyCode.setPurpose("RECOVERY");
        verifyCode.setUsed(false);
        verifyCode.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        verifyCodeMapper.insert(verifyCode);

        redisTemplate.opsForValue().set(rateKey, "1", 60, TimeUnit.SECONDS);

        log.info("密码重置验证码: email={}, code={}", email, code);

        if (mailService.isMailEnabledAndConfigured()) {
            try {
                mailService.sendPlainText(email, "密码重置验证码",
                        "您的验证码是: " + code + "\n有效期 5 分钟，请勿泄露给他人。");
            } catch (Exception e) {
                log.warn("密码重置验证码邮件发送失败: email={}, err={}", email, e.getMessage());
            }
        }
    }

    public void resetPassword(PortalPasswordRecoveryRequest request) {
        // 查找验证码
        PortalVerifyCode verifyCode = verifyCodeMapper.selectOne(
            new LambdaQueryWrapper<PortalVerifyCode>()
                .eq(PortalVerifyCode::getEmail, request.getEmail())
                .eq(PortalVerifyCode::getPurpose, "RECOVERY")
                .eq(PortalVerifyCode::getUsed, false)
                .orderByDesc(PortalVerifyCode::getId)
                .last("LIMIT 1")
        );

        if (verifyCode == null || verifyCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("验证码无效或已过期");
        }

        if (!verifyCode.getCode().equals(request.getVerifyCode())) {
            throw new IllegalArgumentException("验证码错误");
        }

        // 标记已使用
        verifyCode.setUsed(true);
        verifyCodeMapper.updateById(verifyCode);

        // 查找对应应用的用户
        AppUser appUser = appUserMapper.selectOne(
            new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, request.getAppId())
                .eq(AppUser::getEmail, request.getEmail())
                .eq(AppUser::getDeleted, 0)
        );

        if (appUser == null) {
            throw new IllegalArgumentException("该应用无此用户");
        }

        // 更新密码
        appUser.setPassword(ENCODER.encode(request.getNewPassword()));
        appUser.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(appUser);
    }

    public void sendEmailVerifyCode(String email) {
        String rateKey = CAPTCHA_RATE_PREFIX + "email:" + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateKey))) {
            throw new IllegalStateException("发送过于频繁，请60秒后重试");
        }

        String code = String.valueOf((int) (Math.random() * 900000 + 100000));

        PortalVerifyCode verifyCode = new PortalVerifyCode();
        verifyCode.setEmail(email);
        verifyCode.setCode(code);
        verifyCode.setPurpose("EMAIL_CHANGE");
        verifyCode.setUsed(false);
        verifyCode.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        verifyCodeMapper.insert(verifyCode);

        redisTemplate.opsForValue().set(rateKey, "1", 60, TimeUnit.SECONDS);

        log.info("邮箱验证码: email={}, code={}", email, code);

        if (mailService.isMailEnabledAndConfigured()) {
            try {
                mailService.sendPlainText(email, "邮箱验证码",
                        "您的验证码是: " + code + "\n有效期 5 分钟，请勿泄露给他人。");
            } catch (Exception e) {
                log.warn("邮箱验证码邮件发送失败: email={}, err={}", email, e.getMessage());
            }
        }
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

    private void recordFailure(String accountKey, String ipKey) {
        redisTemplate.opsForValue().increment(accountKey);
        redisTemplate.expire(accountKey, 15, TimeUnit.MINUTES);
        redisTemplate.opsForValue().increment(ipKey);
        redisTemplate.expire(ipKey, 15, TimeUnit.MINUTES);
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
