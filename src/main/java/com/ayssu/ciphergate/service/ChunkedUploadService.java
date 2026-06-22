package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.config.MinioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkedUploadService {

    private final MinioObjectService minioObjectService;
    private final MinioProperties minioProperties;

    private final ConcurrentHashMap<String, UploadSession> sessions = new ConcurrentHashMap<>();

    private static final long DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB
    private static final long MAX_FILE_SIZE = 512 * 1024 * 1024; // 512MB

    public static class UploadSession {
        public String uploadId;
        public String objectKey;
        public long chunkSize;
        public long totalSize;
        public int totalChunks;
        public int uploadedChunks;
        public List<String> etags;
        public long createdAt;

        public UploadSession(String uploadId, String objectKey, long chunkSize, long totalSize, int totalChunks) {
            this.uploadId = uploadId;
            this.objectKey = objectKey;
            this.chunkSize = chunkSize;
            this.totalSize = totalSize;
            this.totalChunks = totalChunks;
            this.uploadedChunks = 0;
            this.etags = new ArrayList<>();
            this.createdAt = System.currentTimeMillis();
        }
    }

    public record InitUploadRequest(String objectKey, long totalSize, Long chunkSize) {}
    
    public record InitUploadResponse(String uploadId, String objectKey, long chunkSize, int totalChunks) {}
    
    public record UploadChunkRequest(String uploadId, int chunkNumber, MultipartFile file) {}
    
    public record UploadChunkResponse(int chunkNumber, int uploadedChunks, int totalChunks) {}
    
    public record CompleteUploadRequest(String uploadId) {}
    
    public record UploadInfo(String uploadId, String objectKey, long totalSize, int uploadedChunks, int totalChunks) {}

    public InitUploadResponse initUpload(InitUploadRequest request) {
        if (request.totalSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小超过限制: " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }

        long chunkSize = request.chunkSize() != null ? request.chunkSize() : DEFAULT_CHUNK_SIZE;
        int totalChunks = (int) Math.ceil((double) request.totalSize() / chunkSize);
        
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        
        UploadSession session = new UploadSession(uploadId, request.objectKey(), chunkSize, request.totalSize(), totalChunks);
        sessions.put(uploadId, session);
        
        log.info("Init chunked upload: uploadId={}, objectKey={}, totalSize={}, chunkSize={}, totalChunks={}",
                uploadId, request.objectKey(), request.totalSize(), chunkSize, totalChunks);
        
        return new InitUploadResponse(uploadId, request.objectKey(), chunkSize, totalChunks);
    }

    public UploadChunkResponse uploadChunk(UploadChunkRequest request) {
        UploadSession session = sessions.get(request.uploadId());
        if (session == null) {
            throw new IllegalArgumentException("上传会话不存在或已过期");
        }

        if (request.chunkNumber() < 1 || request.chunkNumber() > session.totalChunks) {
            throw new IllegalArgumentException("分片编号无效: " + request.chunkNumber());
        }

        try {
            String chunkKey = session.objectKey + ".chunk." + request.chunkNumber();
            
            minioObjectService.uploadBinaryDefaultBucket(
                chunkKey, 
                request.file(), 
                "application/octet-stream"
            );
            
            session.uploadedChunks++;
            log.info("Uploaded chunk {}/{} for uploadId={}", request.chunkNumber(), session.totalChunks, request.uploadId());
            
            return new UploadChunkResponse(request.chunkNumber(), session.uploadedChunks, session.totalChunks);
        } catch (Exception e) {
            throw new RuntimeException("分片上传失败: " + e.getMessage(), e);
        }
    }

    public void completeUpload(CompleteUploadRequest request) {
        UploadSession session = sessions.get(request.uploadId());
        if (session == null) {
            throw new IllegalArgumentException("上传会话不存在或已过期");
        }

        if (session.uploadedChunks != session.totalChunks) {
            throw new IllegalArgumentException("分片未全部上传: " + session.uploadedChunks + "/" + session.totalChunks);
        }

        try {
            // Clean up chunks
            for (int i = 1; i <= session.totalChunks; i++) {
                String chunkKey = session.objectKey + ".chunk." + i;
                try {
                    minioObjectService.deleteObject(minioProperties.getBucket(), chunkKey);
                } catch (Exception e) {
                    log.warn("Failed to delete chunk {}: {}", chunkKey, e.getMessage());
                }
            }
            
            sessions.remove(request.uploadId());
            log.info("Completed chunked upload: uploadId={}, objectKey={}", request.uploadId(), session.objectKey);
        } catch (Exception e) {
            throw new RuntimeException("合并分片失败: " + e.getMessage(), e);
        }
    }

    public UploadInfo getUploadInfo(String uploadId) {
        UploadSession session = sessions.get(uploadId);
        if (session == null) {
            return null;
        }
        return new UploadInfo(
            session.uploadId,
            session.objectKey,
            session.totalSize,
            session.uploadedChunks,
            session.totalChunks
        );
    }

    public void abortUpload(String uploadId) {
        UploadSession session = sessions.remove(uploadId);
        if (session != null) {
            // Clean up uploaded chunks
            for (int i = 1; i <= session.uploadedChunks; i++) {
                String chunkKey = session.objectKey + ".chunk." + i;
                try {
                    minioObjectService.deleteObject(minioProperties.getBucket(), chunkKey);
                } catch (Exception e) {
                    log.warn("Failed to delete chunk during abort: {}", chunkKey);
                }
            }
            log.info("Aborted chunked upload: uploadId={}", uploadId);
        }
    }

    public void cleanupExpiredSessions(long maxAgeMs) {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            if (now - entry.getValue().createdAt > maxAgeMs) {
                log.info("Cleaning up expired upload session: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }
}
