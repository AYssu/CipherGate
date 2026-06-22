package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.config.MinioProperties;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.InetAddress;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoIpService {

    public static final String CONFIG_GEOIP_ENABLED = "geoip.enabled";
    public static final String CONFIG_GEOIP_COUNTRY_OBJECT_KEY = "geoip.country.object-key";
    public static final String CONFIG_GEOIP_CITY_OBJECT_KEY = "geoip.city.object-key";

    private final SystemConfigService systemConfigService;
    private final MinioObjectService minioObjectService;
    private final MinioProperties minioProperties;
    private final Ip2RegionService ip2RegionService;

    private volatile DatabaseReader countryReader;
    private volatile DatabaseReader cityReader;
    @Getter
    private volatile String countryDbPath;
    @Getter
    private volatile String cityDbPath;
    @Getter
    private volatile boolean ready;
    @Getter
    private volatile String lastError;
    @Getter
    private volatile String provider; // "maxmind" or "ip2region"

    @PostConstruct
    public void init() {
        reloadReaders();
    }

    public boolean isEnabled() {
        return "true".equalsIgnoreCase(systemConfigService.getConfigValue(CONFIG_GEOIP_ENABLED, "false"));
    }

    public synchronized void reloadReaders() {
        closeReaders();
        ready = false;
        lastError = null;
        provider = null;
        
        // 先尝试 ip2region
        if (ip2RegionService.isEnabled() && ip2RegionService.isReady()) {
            ready = true;
            provider = "ip2region";
            log.info("GeoIP using ip2region provider");
            return;
        }
        
        // 回退到 MaxMind
        countryDbPath = systemConfigService.getConfigValue(CONFIG_GEOIP_COUNTRY_OBJECT_KEY, "");
        cityDbPath = systemConfigService.getConfigValue(CONFIG_GEOIP_CITY_OBJECT_KEY, "");
        if (!isEnabled()) {
            return;
        }
        if (!minioProperties.isEnabled()) {
            lastError = "MinIO 未启用，无法加载 GeoIP 库";
            return;
        }
        if (!StringUtils.hasText(countryDbPath) || !StringUtils.hasText(cityDbPath)) {
            lastError = "未配置 GeoIP 对象键";
            return;
        }
        try (InputStream countryIn = minioObjectService.download(minioProperties.getBucket(), countryDbPath);
             InputStream cityIn = minioObjectService.download(minioProperties.getBucket(), cityDbPath)) {
            countryReader = new DatabaseReader.Builder(countryIn).build();
            cityReader = new DatabaseReader.Builder(cityIn).build();
            ready = true;
            provider = "maxmind";
            log.info("GeoIP readers loaded from MinIO: bucket={}, countryKey={}, cityKey={}",
                    minioProperties.getBucket(), countryDbPath, cityDbPath);
        } catch (Exception e) {
            lastError = e.getMessage();
            log.warn("GeoIP readers load failed: {}", e.getMessage());
        }
    }

    public Optional<GeoIpResult> resolve(String ip) {
        if (!ready || !StringUtils.hasText(ip)) {
            return Optional.empty();
        }
        
        // 优先使用 ip2region
        if ("ip2region".equals(provider)) {
            Ip2RegionService.Ip2RegionResult result = ip2RegionService.resolve(ip);
            if (result != null) {
                return Optional.of(new GeoIpResult(
                    result.ip(),
                    null, // ip2region 不提供国家代码
                    result.country(),
                    result.province(),
                    result.city()
                ));
            }
            return Optional.empty();
        }
        
        // 回退到 MaxMind
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isSiteLocalAddress()) {
                return Optional.empty();
            }
            CountryResponse country = countryReader.country(addr);
            CityResponse city = cityReader.city(addr);
            String countryName = country.getCountry() == null ? null
                    : country.getCountry().getNames().getOrDefault("zh-CN", country.getCountry().getName());
            String countryCode = country.getCountry() == null ? null : country.getCountry().getIsoCode();
            String province = city.getMostSpecificSubdivision() == null ? null
                    : city.getMostSpecificSubdivision().getNames().getOrDefault("zh-CN", city.getMostSpecificSubdivision().getName());
            String cityName = city.getCity() == null ? null
                    : city.getCity().getNames().getOrDefault("zh-CN", city.getCity().getName());
            return Optional.of(new GeoIpResult(ip, countryCode, countryName, province, cityName));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void closeReaders() {
        try {
            if (countryReader != null) {
                countryReader.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (cityReader != null) {
                cityReader.close();
            }
        } catch (Exception ignored) {
        }
        countryReader = null;
        cityReader = null;
    }

    public record GeoIpResult(String ip, String countryCode, String country, String province, String city) {
    }
}
