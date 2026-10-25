package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.doc.entity.DocItem;
import com.ayssu.ciphergate.doc.mapper.DocItemMapper;
import com.ayssu.ciphergate.entity.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkedUploadServiceTest {
    @TempDir Path root;
    MinioObjectService objects;
    UserService users;
    DocItemMapper docs;
    ChunkedUploadService service;

    @BeforeEach void setup() throws Exception {
        objects = mock(MinioObjectService.class);
        users = mock(UserService.class);
        docs = mock(DocItemMapper.class);
        when(users.hasPermission(anyLong(), eq("DOC_ITEM_CREATE"))).thenReturn(true);
        when(docs.selectById(10L)).thenReturn(new DocItem());
        service = new ChunkedUploadService(objects, users, docs, root.toString());
        service.initializeStaging();
        login(1L);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void login(long id) {
        User user = new User(); user.setId(id);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
    ChunkedUploadService.InitUploadResponse init(long size, long chunk) {
        return service.initUpload(new ChunkedUploadService.InitUploadRequest("doc-attachments/10/a.zip", size, chunk));
    }
    ChunkedUploadService.UploadChunkResponse put(String id, int n, byte[] bytes) {
        return service.uploadChunk(new ChunkedUploadService.UploadChunkRequest(id, n, new MockMultipartFile("file", bytes)));
    }
    @Test void assignsUniqueServerKeyAndRejectsOtherNamespaces() {
        var a = init(4, 2); var b = init(4, 2);
        assertNotEquals(a.objectKey(), b.objectKey());
        assertTrue(a.objectKey().startsWith("doc-attachments/10/1_"));
        for (String key : List.of("app-updates/2/a.zip", "plugins/a.jar", "doc-attachments/10/../a", "doc-attachments/10/..", "doc-attachments/10/a\\b"))
            assertThrows(IllegalArgumentException.class, () -> service.initUpload(new ChunkedUploadService.InitUploadRequest(key, 2, 1L)));
    }
    @Test void allOperationsRejectOtherOwnerAndRevokedPermissions() {
        var a = init(4, 2); login(2L);
        assertThrows(SecurityException.class, () -> put(a.uploadId(), 1, new byte[2]));
        assertThrows(SecurityException.class, () -> service.getUploadInfo(a.uploadId()));
        assertThrows(SecurityException.class, () -> service.abortUpload(a.uploadId()));
        assertThrows(SecurityException.class, () -> service.completeUpload(new ChunkedUploadService.CompleteUploadRequest(a.uploadId())));
        login(1L); when(users.hasPermission(1L, "DOC_ITEM_CREATE")).thenReturn(false);
        assertThrows(SecurityException.class, () -> service.getUploadInfo(a.uploadId()));
        verifyNoInteractions(objects);
    }
    @Test void requiresLoginExistingDocumentAndValidDeclarations() {
        SecurityContextHolder.clearContext(); assertThrows(SecurityException.class, () -> init(2, 1)); login(1L);
        when(docs.selectById(10L)).thenReturn(null); assertThrows(SecurityException.class, () -> init(2, 1));
        when(docs.selectById(10L)).thenReturn(new DocItem());
        for (long size : new long[]{0, -1, 512L * 1024 * 1024 + 1}) assertThrows(IllegalArgumentException.class, () -> init(size, 1));
        for (long chunk : new long[]{0, -1, 16L * 1024 * 1024 + 1}) assertThrows(IllegalArgumentException.class, () -> init(4, chunk));
        assertThrows(IllegalArgumentException.class, () -> init(4097, 1));
    }
    @Test void retriesAreIdempotentDifferentContentRejectedAndMissingPartCannotComplete() {
        var a = init(4, 2);
        assertEquals(1, put(a.uploadId(), 2, new byte[]{3, 4}).uploadedChunks());
        assertEquals(1, put(a.uploadId(), 2, new byte[]{3, 4}).uploadedChunks());
        assertThrows(IllegalArgumentException.class, () -> put(a.uploadId(), 2, new byte[]{4, 3}));
        assertEquals(1, service.getUploadInfo(a.uploadId()).uploadedChunks());
        assertThrows(IllegalArgumentException.class, () -> service.completeUpload(new ChunkedUploadService.CompleteUploadRequest(a.uploadId())));
        verifyNoInteractions(objects);
    }
    @Test void exactActualLengthCheckedEvenWhenMultipartDeclarationLies() {
        var a = init(4, 2);
        for (byte[] bytes : new byte[][]{new byte[]{1}, new byte[]{1, 2, 3}}) {
            var liar = new MockMultipartFile("file", bytes) { @Override public long getSize() { return 2; } };
            assertThrows(IllegalArgumentException.class, () -> service.uploadChunk(new ChunkedUploadService.UploadChunkRequest(a.uploadId(), 1, liar)));
        }
        assertThrows(IllegalArgumentException.class, () -> put(a.uploadId(), 0, new byte[2]));
        assertThrows(IllegalArgumentException.class, () -> put(a.uploadId(), 3, new byte[2]));
        assertEquals(0, service.getUploadInfo(a.uploadId()).uploadedChunks());
    }
    @Test void concurrentOutOfOrderAndDuplicatePartsProduceExactStreamAndCleanAllParts() throws Exception {
        var a = init(5, 2);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            var jobs = List.<Callable<Void>>of(
                () -> { login(1); put(a.uploadId(), 3, new byte[]{5}); return null; },
                () -> { login(1); put(a.uploadId(), 1, new byte[]{1, 2}); return null; },
                () -> { login(1); put(a.uploadId(), 2, new byte[]{3, 4}); return null; },
                () -> { login(1); put(a.uploadId(), 1, new byte[]{1, 2}); return null; });
            for (Future<Void> task : pool.invokeAll(jobs)) task.get();
        } finally { pool.shutdownNow(); }
        assertEquals(3, service.getUploadInfo(a.uploadId()).uploadedChunks());
        doAnswer(invocation -> {
            assertArrayEquals(new byte[]{1,2,3,4,5}, ((InputStream) invocation.getArgument(1)).readAllBytes());
            assertEquals(5L, invocation.<Long>getArgument(2)); return null;
        }).when(objects).uploadFromStream(eq(a.objectKey()), any(InputStream.class), eq(5L), anyString());
        service.completeUpload(new ChunkedUploadService.CompleteUploadRequest(a.uploadId()));
        verify(objects).uploadFromStream(eq(a.objectKey()), any(), eq(5L), anyString());
        assertFalse(Files.exists(root.resolve(a.uploadId())));
        assertThrows(SecurityException.class, () -> service.getUploadInfo(a.uploadId()));
    }
    @Test void concurrentCompletionPublishesOnlyOnce() throws Exception {
        var a = init(2, 2); put(a.uploadId(), 1, new byte[]{1,2});
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> complete = () -> {
            login(1); start.await();
            try { service.completeUpload(new ChunkedUploadService.CompleteUploadRequest(a.uploadId())); return true; }
            catch (SecurityException | IllegalArgumentException e) { return false; }
        };
        try {
            var one = pool.submit(complete); var two = pool.submit(complete); start.countDown();
            assertNotEquals(one.get(), two.get());
        } finally { pool.shutdownNow(); }
        verify(objects, times(1)).uploadFromStream(eq(a.objectKey()), any(), eq(2L), anyString());
    }
    @Test void largeUploadUsesBoundedInputReadsAndDiskBackedFinalStream() throws Exception {
        int size = 2 * 1024 * 1024;
        var a = init(size, size);
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]) {
            @Override public long getSize() { return size; }
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    int remaining = size;
                    @Override public int read() { return remaining-- > 0 ? 7 : -1; }
                    @Override public int read(byte[] buffer, int offset, int length) {
                        assertTrue(length <= 8192, "source reads must stay bounded");
                        if (remaining == 0) return -1;
                        int n = Math.min(remaining, length); java.util.Arrays.fill(buffer, offset, offset+n, (byte) 7);
                        remaining -= n; return n;
                    }
                };
            }
        };
        service.uploadChunk(new ChunkedUploadService.UploadChunkRequest(a.uploadId(), 1, file));
        doAnswer(invocation -> {
            InputStream input = invocation.getArgument(1);
            assertFalse(input instanceof ByteArrayInputStream);
            byte[] buffer = new byte[4096]; long total = 0; int n;
            while ((n = input.read(buffer)) != -1) { total += n; assertEquals(7, buffer[n-1]); }
            assertEquals(size, total); return null;
        }).when(objects).uploadFromStream(eq(a.objectKey()), any(), eq((long) size), anyString());
        service.completeUpload(new ChunkedUploadService.CompleteUploadRequest(a.uploadId()));
        assertFalse(Files.exists(root.resolve(a.uploadId())));
    }
    @Test void objectStoreFailureLeavesPartsRetryableWithoutMergedFile() {
        var a = init(2, 2); put(a.uploadId(), 1, new byte[]{1,2});
        doThrow(new IllegalStateException("offline")).when(objects).uploadFromStream(anyString(), any(), anyLong(), anyString());
        assertThrows(IllegalStateException.class, () -> service.completeUpload(new ChunkedUploadService.CompleteUploadRequest(a.uploadId())));
        assertEquals(1, service.getUploadInfo(a.uploadId()).uploadedChunks());
        assertFalse(Files.exists(root.resolve(a.uploadId()).resolve("merged")));
        assertTrue(Files.exists(root.resolve(a.uploadId()).resolve("1")));
    }
    @Test void outOfOrderAbortAndExpiryRemoveActualFiles() {
        var a = init(6, 2); put(a.uploadId(), 3, new byte[2]); service.abortUpload(a.uploadId());
        assertFalse(Files.exists(root.resolve(a.uploadId())));
        var b = init(6, 2); put(b.uploadId(), 3, new byte[2]); service.cleanupExpiredSessions(0);
        assertFalse(Files.exists(root.resolve(b.uploadId())));
        assertThrows(SecurityException.class, () -> service.getUploadInfo(b.uploadId()));
    }
    @Test void restartReclaimsOrphansWithoutDeletingUnrelatedOrSymlinkTargets() throws Exception {
        var a = init(6, 2); put(a.uploadId(), 3, new byte[2]);
        Path unrelated = Files.createDirectory(root.resolve("keep-other-data"));
        Path outside = Files.createTempDirectory("ciphergate-upload-target-");
        try {
            Path marker = Files.writeString(outside.resolve("keep"), "safe");
            Path link = root.resolve("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"); Files.createSymbolicLink(link, outside);
            var restarted = new ChunkedUploadService(objects, users, docs, root.toString()); restarted.initializeStaging();
            assertFalse(Files.exists(root.resolve(a.uploadId())));
            assertTrue(Files.exists(unrelated)); assertTrue(Files.exists(marker));
            assertThrows(SecurityException.class, () -> restarted.getUploadInfo(a.uploadId()));
        } finally {
            Files.deleteIfExists(outside.resolve("keep")); Files.deleteIfExists(outside);
        }
    }
    @Test void limitsActiveSessionsAndReservedBytes() {
        for (int i=0; i<4; i++) init(2, 1);
        assertThrows(IllegalArgumentException.class, () -> init(2, 1));
        service.cleanupExpiredSessions(0);
        var first = init(512L*1024*1024, 16L*1024*1024);
        init(512L*1024*1024, 16L*1024*1024);
        assertThrows(IllegalArgumentException.class, () -> init(1, 1)); // Owner cannot reserve the full global budget.
        login(2L);
        assertDoesNotThrow(() -> init(512L*1024*1024, 16L*1024*1024));
        assertDoesNotThrow(() -> init(512L*1024*1024, 16L*1024*1024));
        login(3L); assertThrows(IllegalArgumentException.class, () -> init(1, 1)); // Global budget still applies.
        login(1L); service.abortUpload(first.uploadId());
        login(3L); assertDoesNotThrow(() -> init(1, 1)); // Cancellation releases the reservation.
    }
}
