package com.ayssu.ciphergate.service.mail;

import com.ayssu.ciphergate.service.SystemConfigService;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 使用系统配置（数据库）中的 SMTP 参数发送邮件，供业务模块复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSmtpMailService {

    private final SystemConfigService systemConfigService;

    public boolean isMailEnabledAndConfigured() {
        if (!"true".equalsIgnoreCase(systemConfigService.getConfigValue("email.enabled", "false"))) {
            return false;
        }
        if (!StringUtils.hasText(systemConfigService.getConfigValue("email.smtp.host", ""))) {
            return false;
        }
        if (!StringUtils.hasText(systemConfigService.getConfigValue("email.smtp.username", ""))) {
            return false;
        }
        if (!StringUtils.hasText(systemConfigService.getConfigValue("email.smtp.password", ""))) {
            return false;
        }
        return StringUtils.hasText(systemConfigService.getConfigValue("email.from", ""));
    }

    /**
     * 发送纯文本邮件（UTF-8）。
     */
    public void sendPlainText(String to, String subject, String text) {
        if (!isMailEnabledAndConfigured()) {
            throw new IllegalStateException("邮件服务未启用或未完整配置 SMTP");
        }
        if (!StringUtils.hasText(to)) {
            throw new IllegalArgumentException("收件人不能为空");
        }

        String host = systemConfigService.getConfigValue("email.smtp.host", "").trim();
        int port = parsePort(systemConfigService.getConfigValue("email.smtp.port", "587"));
        String username = systemConfigService.getConfigValue("email.smtp.username", "").trim();
        String password = systemConfigService.getConfigValue("email.smtp.password", "");
        String from = systemConfigService.getConfigValue("email.from", "").trim();
        String fromDisplayName = systemConfigService.getConfigValue("email.from.display-name", "").trim();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.mime.charset", StandardCharsets.UTF_8.name());
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        // QQ/部分厂商对 EHLO 主机名校验极严：本机名为中文或非 ASCII 时会返回 502 Invalid input
        props.put("mail.smtp.localhost", "ciphergate");
        props.put("mail.smtp.ssl.trust", host);
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
        } else {
            props.put("mail.smtp.ssl.enable", "false");
            props.put("mail.smtp.starttls.enable", "true");
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            // QQ 邮箱：发件人地址须与开启 SMTP 的帐号一致，否则易被拒
            if (host.toLowerCase().contains("qq.com")
                    && StringUtils.hasText(username)
                    && StringUtils.hasText(from)
                    && username.contains("@")
                    && !from.equalsIgnoreCase(username.trim())) {
                log.warn("QQ SMTP 建议将「发件人邮箱」与「SMTP 用户名」设为同一 QQ 邮箱，当前 from={} username={}", from, username);
            }
            helper.setFrom(buildFromAddress(from, fromDisplayName));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            sender.send(message);
        } catch (Exception e) {
            log.warn("SMTP 发送失败: to={}, subject={}, err={}", to, subject, e.getMessage());
            throw new IllegalStateException("邮件发送失败，请检查 SMTP 配置或稍后重试", e);
        }
    }

    /**
     * 发件人显示名（可选）：收件箱「发件人」一列展示的名称；地址仍为 {@code email.from}。
     */
    private static InternetAddress buildFromAddress(String address, String displayName) {
        try {
            if (StringUtils.hasText(displayName)) {
                String personal = displayName.length() > 100 ? displayName.substring(0, 100) : displayName;
                return new InternetAddress(address, personal, StandardCharsets.UTF_8.name());
            }
            return new InternetAddress(address);
        } catch (Exception e) {
            try {
                return new InternetAddress(address);
            } catch (Exception e2) {
                throw new IllegalArgumentException("发件人邮箱格式无效", e2);
            }
        }
    }

    private static int parsePort(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 587;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 587;
        }
    }
}
