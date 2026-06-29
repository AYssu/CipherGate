package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.config.MinioProperties;
import com.ayssu.ciphergate.service.MinioObjectService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioObjectServiceImpl implements MinioObjectService {

    @Nullable
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public String uploadJar(String objectKey, MultipartFile file) {
        if (minioClient == null) {
            throw new RuntimeException("MinIO 未启用，无法上传插件");
        }
        try {
            ensureBucket();
            try (InputStream stream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectKey)
                        .stream(stream, file.getSize(), -1)
                        .contentType("application/java-archive")
                        .build());
            }
            return minioProperties.getBucket();
        } catch (Exception e) {
            throw new RuntimeException("上传插件到 MinIO 失败", e);
        }
    }

    @Override
    public void uploadBinaryDefaultBucket(String objectKey, MultipartFile file, @Nullable String contentType) {
        if (minioClient == null) {
            throw new RuntimeException("MinIO 未启用，无法上传文件");
        }
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }
        try {
            ensureBucket();
            String ct = StringUtils.hasText(contentType)
                    ? contentType
                    : (StringUtils.hasText(file.getContentType()) ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
            try (InputStream stream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectKey)
                        .stream(stream, file.getSize(), -1)
                        .contentType(ct)
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("上传到 MinIO 失败", e);
        }
    }

    @Override
    public InputStream download(String bucket, String objectKey) {
        if (minioClient == null) {
            throw new RuntimeException("MinIO 未启用，无法下载插件");
        }
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("从 MinIO 下载插件失败", e);
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        if (minioClient == null) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("删除 MinIO 插件对象失败", e);
        }
    }

    @Override
    @Nullable
    public String presignedGetUrlDefaultBucket(@Nullable String objectKey, int expiryMinutes) {
        if (minioClient == null || !StringUtils.hasText(objectKey)) {
            return null;
        }
        String key = objectKey.trim();
        String bucket = minioProperties.getBucket();
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() != null ? e.errorResponse().code() : null;
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                return null;
            }
            log.warn("MinIO statObject failed: bucket={}, key={}", bucket, key, e);
            return null;
        } catch (Exception e) {
            log.warn("MinIO statObject failed: bucket={}, key={}", bucket, key, e);
            return null;
        }
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(expiryMinutes, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO presign GET failed: bucket={}, key={}", bucket, key, e);
            return null;
        }
    }

    @Override
    public long contentLengthDefaultBucket(@Nullable String objectKey) {
        if (minioClient == null || !StringUtils.hasText(objectKey)) {
            return -1L;
        }
        String key = objectKey.trim();
        String bucket = minioProperties.getBucket();
        try {
            return minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build()).size();
        } catch (Exception e) {
            return -1L;
        }
    }

    @Override
    public void uploadFromStream(String objectKey, InputStream stream, long size, String contentType) {
        if (minioClient == null) {
            throw new RuntimeException("MinIO 未启用，无法上传文件");
        }
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("从流上传到 MinIO 失败", e);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(minioProperties.getBucket())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
        }
    }
}
