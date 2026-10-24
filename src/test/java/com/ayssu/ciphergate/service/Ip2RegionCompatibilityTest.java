package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.config.MinioProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Ip2RegionCompatibilityTest {
    private final SystemConfigService config = mock(SystemConfigService.class);
    private final MinioObjectService storage = mock(MinioObjectService.class);
    private final MinioProperties properties = new MinioProperties();
    private final Ip2RegionService service = new Ip2RegionService(config, storage, properties);

    @BeforeEach
    void enableLookup() {
        properties.setEnabled(true);
        properties.setBucket("test-bucket");
        when(config.getConfigValue(Ip2RegionService.CONFIG_IP2REGION_ENABLED, "false"))
                .thenReturn("true");
        when(config.getConfigValue(Ip2RegionService.CONFIG_IP2REGION_OBJECT_KEY, ""))
                .thenReturn("ip2region/database.xdb");
    }

    @Test
    void existingVersionTwoDatabaseRetainsGeographicFieldsAfterSdkUpgrade() {
        loadDatabase(database(2, "中国|华南|广东省|深圳市|电信"));
        assertTrue(service.isReady(), service.getLastError());
        var location = service.resolve("1.2.3.4");
        assertNotNull(location);
        assertEquals("中国", location.country());
        assertEquals("华南", location.region());
        assertEquals("广东省", location.province());
        assertEquals("深圳市", location.city());
        assertEquals("电信", location.isp());
        assertEquals("中国 / 广东省 / 深圳市", location.formatRegion());
        assertNull(service.resolve("1.2.4.5"));
    }

    @Test
    void versionThreeDatabaseDoesNotMistakeIspAndCountryCodeForCity() {
        loadDatabase(database(3, "中国|广东省|深圳市|电信|CN"));
        assertTrue(service.isReady(), service.getLastError());
        var location = service.resolve("1.2.3.4");
        assertNotNull(location);
        assertEquals("中国", location.country());
        assertEquals("广东省", location.province());
        assertEquals("深圳市", location.city());
        assertEquals("电信", location.isp());
        assertEquals("中国 / 广东省 / 深圳市", location.formatRegion());
    }

    @Test
    void replacementDatabaseChangesMappingAndCorruptionDisablesLookup() {
        loadDatabase(database(2, "中国|华南|广东省|深圳市|电信"));
        loadDatabase(database(3, "Australia|Queensland|Brisbane|Example ISP|AU"));
        assertTrue(service.isReady(), service.getLastError());
        assertEquals("Brisbane", service.resolve("1.2.3.4").city());
        loadDatabase(new byte[]{1, 2, 3});
        assertFalse(service.isReady());
        assertNotNull(service.getLastError());
        assertNull(service.resolve("1.2.3.4"));
    }

    private void loadDatabase(byte[] bytes) {
        when(storage.download("test-bucket", "ip2region/database.xdb"))
                .thenAnswer(ignored -> new ByteArrayInputStream(bytes));
        service.reloadSearcher();
    }

    /** Real xdb wire format: one IPv4 range, vector index, segment and UTF-8 payload. */
    private static byte[] database(int version, String region) {
        byte[] text = region.getBytes(StandardCharsets.UTF_8);
        int segment = 256 + 256 * 256 * 8;
        int payload = segment + 14;
        ByteBuffer db = ByteBuffer.allocate(payload + text.length).order(ByteOrder.LITTLE_ENDIAN);
        db.putShort(0, (short) version);
        db.putShort(2, (short) 1);
        db.putInt(8, segment);
        db.putInt(12, segment);
        if (version == 3) {
            db.putShort(16, (short) 4);
            db.putShort(18, (short) 4);
        }
        int vector = 256 + (1 * 256 + 2) * 8;
        db.putInt(vector, segment);
        db.putInt(vector + 4, segment);
        db.putInt(segment, 0x01020300);
        db.putInt(segment + 4, 0x010203ff);
        db.putShort(segment + 8, (short) text.length);
        db.putInt(segment + 10, payload);
        db.position(payload);
        db.put(text);
        return db.array();
    }
}
