package com.ayssu.ciphergate.thirdparty;

import com.ayssu.ciphergate.config.MinioProperties;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.MinioObjectService;
import com.ayssu.ciphergate.thirdparty.controller.ThirdPartyUpdateDownloadController;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyUpdateDownloadTicketService;
import com.ayssu.ciphergate.thirdparty.service.UpdateDownloadTrafficService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ThirdPartyUpdateDownloadTest {
    final ApplicationMapper apps = mock(ApplicationMapper.class);
    final MinioObjectService objects = mock(MinioObjectService.class);
    final UpdateDownloadTrafficService traffic = mock(UpdateDownloadTrafficService.class);
    final ThirdPartyUpdateDownloadTicketService tickets = new ThirdPartyUpdateDownloadTicketService();
    final MinioProperties minio = new MinioProperties();
    final InputStream stream = mock(InputStream.class);
    final Application app = new Application();
    ThirdPartyUpdateDownloadController controller;
    String ticket;

    @BeforeEach void setup() {
        app.setId(7L); app.setOwnerId(1L); app.setAppSecret("test-only");
        app.setStatus(1); app.setUpdateFileStorageKey("updates/app.bin");
        minio.setBucket("test");
        when(apps.selectById(7L)).thenReturn(app);
        when(objects.contentLengthDefaultBucket("updates/app.bin")).thenReturn(100L);
        when(objects.download("test", "updates/app.bin")).thenReturn(stream);
        ticket = tickets.mint(7L, "test-only");
        controller = new ThirdPartyUpdateDownloadController(apps, tickets, objects, minio, traffic);
    }

    @Test void quotaDenialClosesOpenedObjectWithoutReturningItsBytes() throws Exception {
        assertEquals(403, controller.download(ticket).getStatusCode().value());
        verify(stream).close();
    }

    @Test void reservationFailureClosesOpenedObject() throws Exception {
        when(traffic.reserve(app, 100)).thenThrow(new IllegalStateException("database unavailable"));
        assertEquals(500, controller.download(ticket).getStatusCode().value());
        verify(stream).close();
    }

    @Test void acceptedDownloadTransfersStreamOwnershipToResponse() throws Exception {
        when(traffic.reserve(app, 100)).thenReturn(true);
        var response = controller.download(ticket);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(100, response.getHeaders().getContentLength());
        assertSame(stream, response.getBody().getInputStream());
        verify(stream, never()).close();
    }

    @Test void objectOpenFailureDoesNotReserveTraffic() {
        when(objects.download("test", "updates/app.bin")).thenThrow(new IllegalStateException("storage unavailable"));
        assertEquals(500, controller.download(ticket).getStatusCode().value());
        verifyNoInteractions(traffic);
    }
}
