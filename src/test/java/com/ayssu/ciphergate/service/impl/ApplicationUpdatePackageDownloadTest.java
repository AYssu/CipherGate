package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.config.MinioProperties;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.MinioObjectService;
import com.ayssu.ciphergate.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationUpdatePackageDownloadTest {
    private final ApplicationMapper apps = mock(ApplicationMapper.class);
    private final MinioObjectService objects = mock(MinioObjectService.class);
    private final SecurityUtils security = mock(SecurityUtils.class);
    private final MinioProperties properties = new MinioProperties();
    private final ApplicationServiceImpl service = new ApplicationServiceImpl(
            apps, null, null, mock(UserMapper.class), security, null, null, objects, properties, null);
    private Application app;

    @BeforeEach
    void setUp() {
        app = new Application();
        app.setId(1L); app.setOwnerId(7L);
        app.setUpdateFileStorageKey("app-updates/1/01234567-89ab-cdef-0123-456789abcdef_release.zip");
        when(apps.selectById(1L)).thenReturn(app);
    }

    @Test
    void ownerDownloadsThroughDefaultBucketWithoutStoragePrefixInFilename() {
        var stream = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(objects.download(properties.getBucket(), app.getUpdateFileStorageKey())).thenReturn(stream);
        var result = service.downloadUpdatePackage(1L, 7L);
        assertEquals("release.zip", result.filename());
        assertSame(stream, result.stream());
    }

    @Test
    void administratorCanDownload() {
        when(security.isAdmin(9L)).thenReturn(true);
        service.downloadUpdatePackage(1L, 9L);
        verify(objects).download(properties.getBucket(), app.getUpdateFileStorageKey());
    }

    @Test
    void unrelatedUserCannotReadObject() {
        assertThrows(RuntimeException.class, () -> service.downloadUpdatePackage(1L, 8L));
        verifyNoInteractions(objects);
    }

    @Test
    void missingPackageOrOtherApplicationKeyCannotReadObject() {
        app.setUpdateFileStorageKey(null);
        assertThrows(IllegalArgumentException.class, () -> service.downloadUpdatePackage(1L, 7L));
        app.setUpdateFileStorageKey("app-updates/2/release.zip");
        assertThrows(IllegalArgumentException.class, () -> service.downloadUpdatePackage(1L, 7L));
        verifyNoInteractions(objects);
    }
}
