package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.config.MinioProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class Ip2RegionService {

    public static final String CONFIG_IP2REGION_ENABLED = "ip2region.enabled";
    public static final String CONFIG_IP2REGION_OBJECT_KEY = "ip2region.object-key";

    private final SystemConfigService systemConfigService;
    private final MinioObjectService minioObjectService;
    private final MinioProperties minioProperties;

    @Getter
    private volatile Searcher searcher;
    @Getter
    private volatile boolean ready;
    @Getter
    private volatile String lastError;
    @Getter
    private volatile String dbPath;

    @PostConstruct
    public void init() {
        reloadSearcher();
    }

    public boolean isEnabled() {
        return "true".equalsIgnoreCase(systemConfigService.getConfigValue(CONFIG_IP2REGION_ENABLED, "false"));
    }

    public synchronized void reloadSearcher() {
        closeSearcher();
        ready = false;
        lastError = null;
        dbPath = systemConfigService.getConfigValue(CONFIG_IP2REGION_OBJECT_KEY, "");
        
        if (!isEnabled()) {
            return;
        }
        if (!minioProperties.isEnabled()) {
            lastError = "MinIO 未启用，无法加载 ip2region 数据库";
            return;
        }
        if (!StringUtils.hasText(dbPath)) {
            lastError = "未配置 ip2region 对象键";
            return;
        }
        
        try (InputStream in = minioObjectService.download(minioProperties.getBucket(), dbPath)) {
            // Read all bytes into memory
            byte[] contentBuff = toByteArray(in);
            
            // Create searcher from buffer
            searcher = Searcher.newWithBuffer(contentBuff);
            ready = true;
            log.info("ip2region searcher loaded from MinIO: bucket={}, key={}", minioProperties.getBucket(), dbPath);
        } catch (Exception e) {
            lastError = e.getMessage();
            log.warn("ip2region searcher load failed: {}", e.getMessage());
        }
    }

    public Ip2RegionResult resolve(String ip) {
        if (!ready || !StringUtils.hasText(ip) || searcher == null) {
            return null;
        }
        try {
            String result = searcher.search(ip);
            // Format: "country|region|province|city|isp"
            String[] parts = result.split("\\|", -1);
            if (parts.length >= 5) {
                String country = parts[0];
                String region = parts[1];
                String province = parts[2];
                String city = parts[3];
                String isp = parts[4];
                return new Ip2RegionResult(ip, country, region, province, city, isp);
            }
        } catch (Exception e) {
            log.debug("ip2region resolve failed for {}: {}", ip, e.getMessage());
        }
        return null;
    }

    private void closeSearcher() {
        try {
            if (searcher != null) {
                searcher.close();
            }
        } catch (Exception ignored) {
        }
        searcher = null;
    }

    private byte[] toByteArray(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    public record Ip2RegionResult(String ip, String country, String region, String province, String city, String isp) {
        public String formatRegion() {
            StringBuilder sb = new StringBuilder();
            if (StringUtils.hasText(country)) sb.append(country);
            if (StringUtils.hasText(province) && !province.equals(country)) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(province);
            }
            if (StringUtils.hasText(city) && !city.equals(province)) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(city);
            }
            return sb.toString();
        }
    }
}
