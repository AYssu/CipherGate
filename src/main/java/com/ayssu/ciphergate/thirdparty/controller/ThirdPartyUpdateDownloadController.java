package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.config.MinioProperties;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.MinioObjectService;
import com.ayssu.ciphergate.thirdparty.service.UpdateDownloadTrafficService;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyUpdateDownloadTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "三方更新下载", description = "使用短时 ticket 下载应用更新包，不使用 X-App-Key/X-Signature 请求头")
public class ThirdPartyUpdateDownloadController {

    private final ApplicationMapper applicationMapper;
    private final ThirdPartyUpdateDownloadTicketService ticketService;
    private final MinioObjectService minioObjectService;
    private final MinioProperties minioProperties;
    private final UpdateDownloadTrafficService trafficService;

    @GetMapping("/app/update-package")
    @Operation(
            summary = "下载应用更新包",
            description = "使用 /api/v1/app/update-check 返回的短时 ticket 下载；票据默认 5 分钟有效，仅需 ticket 查询参数，无需第三方签名头"
    )
    public ResponseEntity<Resource> download(
            @Parameter(
                    description = "检查更新接口返回并经过 URL 编码的短时下载票据",
                    required = true,
                    example = "<urlencoded-ticket>"
            )
            @RequestParam("ticket") String ticket) {
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

        InputStream in = null;
        try {
            in = minioObjectService.download(bucket, objectKey);
            String filename = Paths.get(objectKey.replace("\\", "/")).getFileName().toString();
            if (!StringUtils.hasText(filename)) {
                filename = "update.bin";
            }
            if (!trafficService.reserve(app, size)) {
                log.warn("更新包状态已变更或流量额度不足，拒绝下载: appId={}, size={}", app.getId(), size);
                return ResponseEntity.status(403).build();
            }
            InputStreamResource body = new InputStreamResource(in);
            ResponseEntity<Resource> response = ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentLength(size)
                    .body(body);
            in = null; // The response now owns the stream; no transaction is held while streaming.
            return response;
        } catch (Exception e) {
            log.warn("update-package download failed: appId={}", app.getId(), e);
            return ResponseEntity.internalServerError().build();
        } finally {
            if (in != null) {
                try { in.close(); }
                catch (Exception ignored) { log.debug("关闭更新包流失败: appId={}", app.getId()); }
            }
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
