package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.config.MinioProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Header;
import org.lionsoul.ip2region.xdb.Version;
import org.lionsoul.ip2region.xdb.LongByteArray;
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

    private record LoadedDatabase(Searcher searcher, int version) {}

    private volatile LoadedDatabase database;

    public Searcher getSearcher() {
        LoadedDatabase current = database;
        return current == null ? null : current.searcher();
    }
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
            LongByteArray contentBuff = Searcher.loadContentFromInputStream(in);
            Header header = Searcher.loadHeaderFromBuffer(contentBuff);
            Searcher.verify(header, contentBuff.length());
            Searcher searcher = Searcher.newWithBuffer(Version.fromHeader(header), contentBuff);
            database = new LoadedDatabase(searcher, header.version);
            ready = true;
            log.info("ip2region searcher loaded from MinIO: bucket={}, key={}", minioProperties.getBucket(), dbPath);
        } catch (Exception e) {
            lastError = e.getMessage();
            log.warn("ip2region searcher load failed: {}", e.getMessage());
        }
    }

    public Ip2RegionResult resolve(String ip) {
        LoadedDatabase current = database;
        if (!ready || !StringUtils.hasText(ip) || current == null) {
            return null;
        }
        try {
            String result = current.searcher().search(ip);
            // Standard XDB v2: country|region|province|city|isp; v3: country|province|city|isp|code.
            String[] parts = result.split("\\|", -1);
            if (parts.length >= 5) {
                String country = parts[0];
                // XDB v2 retains the legacy region column; v3 replaces it with a country code.
                boolean legacy = current.version() == 2;
                String region = legacy ? parts[1] : "";
                String province = parts[legacy ? 2 : 1];
                String city = parts[legacy ? 3 : 2];
                String isp = parts[legacy ? 4 : 3];
                return new Ip2RegionResult(ip, country, region, province, city, isp);
            }
        } catch (Exception e) {
            log.debug("ip2region resolve failed for {}: {}", ip, e.getMessage());
        }
        return null;
    }

    private void closeSearcher() {
        LoadedDatabase previous = database;
        database = null;
        try {
            if (previous != null) {
                previous.searcher().close();
            }
        } catch (Exception ignored) {
        }
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
