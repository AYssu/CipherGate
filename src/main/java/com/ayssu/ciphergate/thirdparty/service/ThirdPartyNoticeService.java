package com.ayssu.ciphergate.thirdparty.service;

import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.MinioObjectService;
import com.ayssu.ciphergate.thirdparty.config.ThirdPartyPublicProperties;
import com.ayssu.ciphergate.thirdparty.dto.AppNoticeRequest;
import com.ayssu.ciphergate.thirdparty.dto.AppNoticeResponse;
import com.ayssu.ciphergate.thirdparty.exception.VersionOutOfRangeException;
import com.ayssu.ciphergate.thirdparty.util.SemverThree;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class ThirdPartyNoticeService {

    private final ApplicationMapper applicationMapper;
    private final MinioObjectService minioObjectService;
    private final ThirdPartyUpdateDownloadTicketService updateDownloadTicketService;
    private final ThirdPartyPublicProperties thirdPartyPublicProperties;

    public AppNoticeResponse getNotice(Long appId, AppNoticeRequest req, HttpServletRequest httpRequest) {
        Application app = applicationMapper.selectById(appId);
        if (app == null) {
            throw new RuntimeException("应用不存在");
        }
        String clientRaw = req != null ? req.getVersion() : null;
        String clientTrim = StringUtils.hasText(clientRaw) ? clientRaw.trim() : null;

        boolean rangeChecked = false;
        if (StringUtils.hasText(clientTrim) && SemverThree.isThreePartNumeric(clientTrim)) {
            assertClientWithinRange(clientTrim, app.getMinVersion(), app.getCurrentVersion());
            rangeChecked = true;
        }

        String currentTrim = StringUtils.hasText(app.getCurrentVersion()) ? app.getCurrentVersion().trim() : null;
        boolean tripleClient = StringUtils.hasText(clientTrim) && SemverThree.isThreePartNumeric(clientTrim);
        boolean tripleCurrent = StringUtils.hasText(currentTrim) && SemverThree.isThreePartNumeric(currentTrim);

        boolean isLatest;
        if (tripleClient && tripleCurrent) {
            isLatest = SemverThree.compare(clientTrim, currentTrim) == 0;
        } else {
            // 无法按 x.x.x 与主线比较时，按「视为已对齐」只返回软件公告
            isLatest = true;
        }

        AppNoticeResponse out = new AppNoticeResponse();
        out.setIsLatestVersion(isLatest);
        out.setClientVersion(clientTrim);
        out.setCurrentVersion(app.getCurrentVersion());
        out.setMinVersion(app.getMinVersion());
        out.setVersionRangeChecked(rangeChecked);

        if (isLatest) {
            out.setNotice(app.getNotice());
            out.setUpdateNotice(null);
            out.setUpdateDownloadUrl(null);
        } else {
            out.setNotice(null);
            out.setUpdateNotice(app.getUpdateNotice());
            out.setUpdateDownloadUrl(buildBackendUpdateDownloadUrl(httpRequest, app));
        }
        return out;
    }

    /**
     * 返回本服务 {@code /api/v1/app/update-package?ticket=} 绝对地址（便于网关代理）；票据有效期见
     * {@link ThirdPartyUpdateDownloadTicketService}（默认 5 分钟）。
     */
    private String buildBackendUpdateDownloadUrl(HttpServletRequest request, Application app) {
        String key = app.getUpdateFileStorageKey();
        if (!StringUtils.hasText(key)) {
            return null;
        }
        if (minioObjectService.contentLengthDefaultBucket(key.trim()) < 0) {
            return null;
        }
        String ticket = updateDownloadTicketService.mint(app.getId(), app.getAppSecret());
        String enc = URLEncoder.encode(ticket, StandardCharsets.UTF_8);
        String base = StringUtils.hasText(thirdPartyPublicProperties.getPublicBaseUrl())
                ? trimTrailingSlash(thirdPartyPublicProperties.getPublicBaseUrl().trim())
                : inferRequestBaseUrl(request);
        return base + "/api/v1/app/update-package?ticket=" + enc;
    }

    private static String trimTrailingSlash(String s) {
        if (s.endsWith("/")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String inferRequestBaseUrl(HttpServletRequest r) {
        String scheme = r.getScheme();
        String host = r.getServerName();
        int port = r.getServerPort();
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        if (("http".equalsIgnoreCase(scheme) && port != 80)
                || ("https".equalsIgnoreCase(scheme) && port != 443)) {
            sb.append(':').append(port);
        }
        String cp = r.getContextPath();
        if (StringUtils.hasText(cp)) {
            sb.append(cp);
        }
        return trimTrailingSlash(sb.toString());
    }

    /**
     * 闭区间 [minVersion, currentVersion]；服务端某端未配置合法 x.x.x 则该端不约束。
     */
    private void assertClientWithinRange(String client, String minVersion, String currentVersion) {
        String min = StringUtils.hasText(minVersion) ? minVersion.trim() : null;
        String max = StringUtils.hasText(currentVersion) ? currentVersion.trim() : null;

        boolean hasMin = min != null && SemverThree.isThreePartNumeric(min);
        boolean hasMax = max != null && SemverThree.isThreePartNumeric(max);

        if (!hasMin && !hasMax) {
            return;
        }
        if (hasMin && SemverThree.compare(client, min) < 0) {
            throw new VersionOutOfRangeException("客户端版本过低，最低支持 " + min);
        }
        if (hasMax && SemverThree.compare(client, max) > 0) {
            throw new VersionOutOfRangeException("客户端版本过高，当前最高 " + max);
        }
    }
}
