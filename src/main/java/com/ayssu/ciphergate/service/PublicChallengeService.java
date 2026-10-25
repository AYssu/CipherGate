package com.ayssu.ciphergate.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.ayssu.ciphergate.dto.PublicChallengeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicChallengeService {
    private final StringRedisTemplate redis;
    private final PublicAbuseGuard guard;
    private static final Set<String> PURPOSES = Set.of("register-email", "license-query", "license-unbind", "app-user-query", "trial-query", "trial-apply");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static String key(String id) { return "public:challenge:" + id; }
    public Map<String, String> issue(String purpose, String ip) {
        if (!PURPOSES.contains(purpose)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证用途无效");
        guard.check("public-challenge", ip, 40, Duration.ofMinutes(5));
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(140, 44, 5, 40);
        captcha.setGenerator(new cn.hutool.captcha.generator.CodeGenerator() {
            public String generate() { return String.format(java.util.Locale.ROOT, "%05d", RANDOM.nextInt(100000)); }
            public boolean verify(String code, String input) { return code.equals(input); }
        });
        String code = captcha.getCode();
        String id = UUID.randomUUID().toString();
        redis.opsForValue().set(key(id), purpose + ":" + PublicAbuseGuard.digest(ip) + ":" + code, Duration.ofMinutes(3));
        return Map.of("captchaId", id, "image", captcha.getImageBase64Data());
    }
    public void verify(String purpose, String ip, String subject, PublicChallengeRequest request) {
        guard.check("public-" + purpose + "-ip", ip, 20, Duration.ofMinutes(5));
        guard.check("public-" + purpose + "-subject", subject, 10, Duration.ofMinutes(15));
        String value = redis.opsForValue().getAndDelete(key(request.getCaptchaId()));
        String expected = purpose + ":" + PublicAbuseGuard.digest(ip) + ":" + request.getCaptchaCode();
        if (value == null || !java.security.MessageDigest.isEqual(value.getBytes(java.nio.charset.StandardCharsets.UTF_8), expected.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图形验证码错误或已过期，请刷新后重试");
    }
}
