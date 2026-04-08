package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.config.MinioProperties;
import com.ayssu.ciphergate.service.MinioObjectService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

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

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(minioProperties.getBucket())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
        }
    }
}
