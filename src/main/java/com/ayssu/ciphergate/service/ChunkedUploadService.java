package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.doc.mapper.DocItemMapper;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.util.AuthUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Single-instance upload staging. Sessions intentionally do not survive a process restart. */
@Slf4j
@Service
public class ChunkedUploadService {
    private static final long DEFAULT_CHUNK_SIZE = 5L * 1024 * 1024;
    private static final long MAX_CHUNK_SIZE = 16L * 1024 * 1024;
    private static final long MAX_FILE_SIZE = 512L * 1024 * 1024;
    private static final long MAX_RESERVED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final long MAX_USER_RESERVED_BYTES = 1024L * 1024 * 1024;
    private static final long SESSION_TTL_MS = 24L * 60 * 60 * 1000;
    private static final Pattern DOC_KEY = Pattern.compile("doc-attachments/([1-9][0-9]*)/([^/\\\\\\p{Cntrl}]{1,200})");
    private static final Pattern SESSION_ID = Pattern.compile("[a-f0-9]{32}");
    private final MinioObjectService objects;
    private final UserService users;
    private final DocItemMapper docs;
    private final Path stagingRoot;
    private final ConcurrentHashMap<String, UploadSession> sessions = new ConcurrentHashMap<>();

    public ChunkedUploadService(MinioObjectService objects, UserService users, DocItemMapper docs,
            @Value("${app.upload.staging-directory:${java.io.tmpdir}/ciphergate-upload-staging}") String stagingDirectory) {
        this.objects = objects;
        this.users = users;
        this.docs = docs;
        this.stagingRoot = Path.of(stagingDirectory).toAbsolutePath().normalize();
    }

    private static final class UploadSession {
        final String id, key;
        final Long ownerId, docId;
        final long chunkSize, size, createdAt = System.currentTimeMillis();
        final int totalChunks;
        final Set<Integer> chunks = new HashSet<>();
        boolean closed;
        UploadSession(String id, String key, Long ownerId, Long docId, long chunkSize, long size, int totalChunks) {
            this.id = id; this.key = key; this.ownerId = ownerId; this.docId = docId;
            this.chunkSize = chunkSize; this.size = size; this.totalChunks = totalChunks;
        }
    }

    public record InitUploadRequest(String objectKey, long totalSize, Long chunkSize) {}
    public record InitUploadResponse(String uploadId, String objectKey, long chunkSize, int totalChunks) {}
    public record UploadChunkRequest(String uploadId, int chunkNumber, MultipartFile file) {}
    public record UploadChunkResponse(int chunkNumber, int uploadedChunks, int totalChunks) {}
    public record CompleteUploadRequest(String uploadId) {}
    public record UploadInfo(String uploadId, String objectKey, long totalSize, int uploadedChunks, int totalChunks) {}

    @PostConstruct
    public void initializeStaging() throws IOException {
        Files.createDirectories(stagingRoot);
        if (Files.isSymbolicLink(stagingRoot)) throw new IOException("上传暂存目录不能是符号链接");
        var permissions = Files.getFileAttributeView(stagingRoot, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (permissions != null) permissions.setPermissions(Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        // The dedicated directory must not be shared by multiple backend processes.
        // A restart invalidates sessions; remove their local parts instead of leaving remote objects behind.
        cleanOrphanDirectories(true);
    }

    public InitUploadResponse initUpload(InitUploadRequest request) {
        Long ownerId = currentUserId();
        if (request == null || request.totalSize() <= 0 || request.totalSize() > MAX_FILE_SIZE)
            throw new IllegalArgumentException("文件大小必须在 1 字节至 512MB 之间");
        long chunkSize = request.chunkSize() == null ? DEFAULT_CHUNK_SIZE : request.chunkSize();
        if (chunkSize <= 0 || chunkSize > MAX_CHUNK_SIZE)
            throw new IllegalArgumentException("分片大小必须在 1 字节至 16MB 之间");
        long count = (request.totalSize() + chunkSize - 1) / chunkSize;
        if (count > 4096) throw new IllegalArgumentException("分片数量不能超过 4096");
        var match = DOC_KEY.matcher(Objects.requireNonNullElse(request.objectKey(), ""));
        if (!match.matches() || match.group(2).equals(".") || match.group(2).equals(".."))
            throw new IllegalArgumentException("仅支持文档附件上传，请使用对应的应用或插件上传接口");
        Long docId;
        try { docId = Long.valueOf(match.group(1)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("文档编号无效"); }
        checkDocumentPermission(ownerId, docId);
        String id = UUID.randomUUID().toString().replace("-", "");
        String key = "doc-attachments/" + docId + "/" + ownerId + "_" + id + "_" + match.group(2);
        UploadSession session = new UploadSession(id, key, ownerId, docId, chunkSize, request.totalSize(), (int) count);
        synchronized (sessions) {
            long reserved = sessions.values().stream().mapToLong(s -> s.size).sum();
            long ownerCount = sessions.values().stream().filter(s -> s.ownerId.equals(ownerId)).count();
            long ownerReserved = sessions.values().stream().filter(s -> s.ownerId.equals(ownerId)).mapToLong(s -> s.size).sum();
            if (sessions.size() >= 64 || ownerCount >= 4 || reserved + session.size > MAX_RESERVED_BYTES
                    || ownerReserved + session.size > MAX_USER_RESERVED_BYTES)
                throw new IllegalArgumentException("待上传任务过多，请完成或取消已有任务后重试");
            try { Files.createDirectories(stagingRoot); Files.createDirectory(directory(session)); }
            catch (IOException e) { throw new IllegalStateException("无法创建上传暂存目录", e); }
            sessions.put(id, session);
        }
        return new InitUploadResponse(id, key, chunkSize, (int) count);
    }

    public UploadChunkResponse uploadChunk(UploadChunkRequest request) {
        UploadSession session = requireSession(request.uploadId());
        synchronized (session) {
            checkActive(session);
            int number = request.chunkNumber();
            if (number < 1 || number > session.totalChunks) throw new IllegalArgumentException("分片编号无效");
            long expected = Math.min(session.chunkSize, session.size - (number - 1L) * session.chunkSize);
            if (request.file() == null || request.file().getSize() != expected)
                throw new IllegalArgumentException("分片大小与声明不一致");
            Path target = directory(session).resolve(Integer.toString(number));
            Path pending = directory(session).resolve("pending");
            try {
                try (InputStream input = request.file().getInputStream(); OutputStream out = Files.newOutputStream(pending)) {
                    copyExactly(input, out, expected);
                }
                if (session.chunks.contains(number)) {
                    if (Files.mismatch(target, pending) != -1) throw new IllegalArgumentException("重复分片内容不一致");
                } else {
                    Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
                    session.chunks.add(number);
                }
                return new UploadChunkResponse(number, session.chunks.size(), session.totalChunks);
            } catch (IOException e) { throw new IllegalStateException("分片暂存失败", e); }
            finally { try { Files.deleteIfExists(pending); } catch (IOException e) { log.warn("无法删除上传暂存片段"); } }
        }
    }

    public void completeUpload(CompleteUploadRequest request) {
        UploadSession session = requireSession(request.uploadId());
        synchronized (session) {
            checkActive(session);
            if (session.chunks.size() != session.totalChunks) throw new IllegalArgumentException("分片未全部上传");
            Path merged = directory(session).resolve("merged");
            try {
                // Disk-backed assembly keeps heap usage independent of total file size (8KB copy buffer).
                try (OutputStream output = Files.newOutputStream(merged)) {
                    for (int i = 1; i <= session.totalChunks; i++) {
                        long expected = Math.min(session.chunkSize, session.size - (i - 1L) * session.chunkSize);
                        try (InputStream input = Files.newInputStream(directory(session).resolve(Integer.toString(i)))) {
                            copyExactly(input, output, expected);
                        }
                    }
                }
                if (Files.size(merged) != session.size) throw new IOException("合并文件大小不一致");
                try (InputStream input = Files.newInputStream(merged)) {
                    objects.uploadFromStream(session.key, input, session.size, "application/octet-stream");
                }
                close(session);
            } catch (IOException e) { throw new IllegalStateException("合并分片失败", e); }
            finally { try { Files.deleteIfExists(merged); } catch (IOException e) { log.warn("无法删除合并暂存文件"); } }
        }
    }

    public UploadInfo getUploadInfo(String uploadId) {
        UploadSession s = requireSession(uploadId);
        synchronized (s) {
            checkActive(s);
            return new UploadInfo(s.id, s.key, s.size, s.chunks.size(), s.totalChunks);
        }
    }

    public void abortUpload(String uploadId) {
        UploadSession s = requireSession(uploadId);
        synchronized (s) { checkActive(s); close(s); }
    }

    private Long currentUserId() {
        User user = AuthUtils.getCurrentUser();
        var authentication = AuthUtils.getAuthentication();
        if (user == null && authentication != null && authentication.getPrincipal() instanceof OAuth2User oauth
                && oauth.getAttribute("id") != null)
            user = users.getUserByGithubId(oauth.getAttribute("id").toString());
        if (user == null || user.getId() == null) throw new SecurityException("用户未登录");
        return user.getId();
    }

    private void checkDocumentPermission(Long ownerId, Long docId) {
        if (!users.hasPermission(ownerId, "DOC_ITEM_CREATE") || docs.selectById(docId) == null)
            throw new SecurityException("无权限上传此文档附件");
    }

    private UploadSession requireSession(String id) {
        Long ownerId = currentUserId();
        UploadSession s = id == null ? null : sessions.get(id);
        if (s == null || !s.ownerId.equals(ownerId)) throw new SecurityException("上传会话不可用");
        checkDocumentPermission(ownerId, s.docId);
        return s;
    }

    private void checkActive(UploadSession s) {
        if (s.closed || System.currentTimeMillis() - s.createdAt >= SESSION_TTL_MS)
            throw new IllegalArgumentException("上传会话不存在或已过期，请重新上传");
    }

    private Path directory(UploadSession s) { return stagingRoot.resolve(s.id); }

    private static void copyExactly(InputStream input, OutputStream output, long expected) throws IOException {
        byte[] buffer = new byte[8192];
        long copied = 0;
        int read;
        while ((read = input.read(buffer, 0, (int) Math.min(buffer.length, expected - copied + 1))) != -1) {
            copied += read;
            if (copied > expected) throw new IllegalArgumentException("分片实际大小超过声明");
            output.write(buffer, 0, read);
        }
        if (copied != expected) throw new IllegalArgumentException("分片实际大小小于声明");
    }

    private void close(UploadSession s) {
        s.closed = true;
        sessions.remove(s.id, s);
        deleteDirectory(directory(s));
    }

    private void deleteDirectory(Path directory) {
        try (var entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(entry);
        } catch (IOException e) { log.warn("上传暂存清理失败，将由定时清理重试: {}", directory.getFileName()); }
    }

    @Scheduled(fixedDelay = 600_000)
    public void cleanupExpiredSessions() { cleanupExpiredSessions(SESSION_TTL_MS); }

    public void cleanupExpiredSessions(long maxAgeMs) {
        long now = System.currentTimeMillis();
        for (UploadSession s : sessions.values()) {
            synchronized (s) { if (now - s.createdAt >= maxAgeMs) close(s); }
        }
        try { cleanOrphanDirectories(false); }
        catch (IOException e) { log.warn("无法扫描上传暂存目录"); }
    }

    private void cleanOrphanDirectories(boolean startup) throws IOException {
        try (var entries = Files.list(stagingRoot)) {
            for (Path entry : entries.toList()) {
                String id = entry.getFileName().toString();
                if (SESSION_ID.matcher(id).matches() && !sessions.containsKey(id)
                        && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)
                        && (startup || System.currentTimeMillis() - Files.getLastModifiedTime(entry).toMillis() >= SESSION_TTL_MS))
                    deleteDirectory(entry);
            }
        }
    }
}
