package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.config.MinioProperties;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.MinioObjectService;
import com.ayssu.ciphergate.service.UserMembershipService;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyUpdateDownloadTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Optional;

/**
 * 更新包经本服务中转（便于网关统一代理），不走三方响应体加密。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ThirdPartyUpdateDownloadController {

    private final ApplicationMapper applicationMapper;
    private final ThirdPartyUpdateDownloadTicketService ticketService;
    private final MinioObjectService minioObjectService;
    private final MinioProperties minioProperties;
    private final UserMembershipService userMembershipService;

    @GetMapping("/app/update-package")
    public ResponseEntity<Resource> download(@RequestParam("ticket") String ticket) {
        Application app = resolveApplication(ticket);
        if (app == null) {
            return ResponseEntity.status(403).build();
        }
        if (app.getStatus() != null && app.getStatus() != 1) {
            return ResponseEntity.status(403).build();
        }
        String key = app.getUpdateFileStorageKey();
        if (!StringUtils.hasText(key)) {
            return ResponseEntity.notFound().build();
        }
        String objectKey = key.trim();
        String bucket = minioProperties.getBucket();
        long size = minioObjectService.contentLengthDefaultBucket(objectKey);
        if (size < 0) {
            return ResponseEntity.notFound().build();
        }

        if (!userMembershipService.checkTrafficQuota(app.getOwnerId(), size)) {
            log.warn("用户流量额度不足，拒绝下载: appId={}, ownerId={}, size={}", app.getId(), app.getOwnerId(), size);
            return ResponseEntity.status(403).build();
        }

        if (app.getTrafficLimit() != null && app.getTrafficLimit() > 0) {
            long appUsed = app.getTrafficUsed() != null ? app.getTrafficUsed() : 0;
            if (appUsed + size > app.getTrafficLimit()) {
                log.warn("应用流量额度不足，拒绝下载: appId={}, appLimit={}, appUsed={}, size={}", app.getId(), app.getTrafficLimit(), appUsed, size);
                return ResponseEntity.status(403).build();
            }
        }

        try {
            InputStream in = minioObjectService.download(bucket, objectKey);
            String filename = Paths.get(objectKey.replace("\\", "/")).getFileName().toString();
            if (!StringUtils.hasText(filename)) {
                filename = "update.bin";
            }
            userMembershipService.consumeTrafficQuota(app.getOwnerId(), size);
            app.setTrafficUsed((app.getTrafficUsed() != null ? app.getTrafficUsed() : 0) + size);
            applicationMapper.updateById(app);
            InputStreamResource body = new InputStreamResource(in);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentLength(size)
                    .body(body);
        } catch (Exception e) {
            log.warn("update-package download failed: appId={}, key={}", app.getId(), objectKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Application resolveApplication(String ticketB64) {
        if (!StringUtils.hasText(ticketB64)) {
            return null;
        }
        String trimmed = ticketB64.trim();
        long appId;
        try {
            String token = new String(Base64.getUrlDecoder().decode(trimmed), StandardCharsets.UTF_8);
            String[] parts = token.split("\\|", 4);
            if (parts.length != 4) {
                return null;
            }
            appId = Long.parseLong(parts[0]);
        } catch (Exception e) {
            return null;
        }
        Application app = applicationMapper.selectById(appId);
        if (app == null || !StringUtils.hasText(app.getAppSecret())) {
            return null;
        }
        Optional<Long> verified = ticketService.verify(trimmed, app.getAppSecret());
        if (verified.isEmpty() || !verified.get().equals(appId)) {
            return null;
        }
        return app;
    }
}
