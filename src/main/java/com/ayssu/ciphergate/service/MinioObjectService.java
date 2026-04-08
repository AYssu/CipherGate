package com.ayssu.ciphergate.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface MinioObjectService {
    String uploadJar(String objectKey, MultipartFile file);

    InputStream download(String bucket, String objectKey);

    void deleteObject(String bucket, String objectKey);
}
