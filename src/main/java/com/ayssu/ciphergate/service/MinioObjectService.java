package com.ayssu.ciphergate.service;

import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface MinioObjectService {
    String uploadJar(String objectKey, MultipartFile file);

    /**
     * 上传到默认桶；{@code contentType} 为空时使用文件的 {@code getContentType()}，再否则 {@code application/octet-stream}。
     */
    void uploadBinaryDefaultBucket(String objectKey, MultipartFile file, @Nullable String contentType);

    InputStream download(String bucket, String objectKey);

    void deleteObject(String bucket, String objectKey);

    /**
     * 对默认桶内对象生成 GET 预签名 URL；对象不存在或 MinIO 未启用时返回 {@code null}。
     */
    @Nullable
    String presignedGetUrlDefaultBucket(@Nullable String objectKey, int expiryMinutes);

    /**
     * 默认桶内对象大小；不存在或未启用 MinIO 时返回 {@code -1}。
     */
    long contentLengthDefaultBucket(@Nullable String objectKey);
}
