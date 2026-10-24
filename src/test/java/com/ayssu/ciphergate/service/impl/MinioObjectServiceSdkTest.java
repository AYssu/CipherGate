package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.config.MinioProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the actual SDK against a local S3 HTTP fixture; no external storage is needed. */
class MinioObjectServiceSdkTest {
    private HttpServer server;
    private MinioClient client;
    private MinioObjectServiceImpl service;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @BeforeEach
    void startStorage() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        client = MinioClient.builder().endpoint(endpoint).region("us-east-1")
                .credentials("test-access", "test-secret-for-local-fixture").build();
        MinioProperties properties = new MinioProperties();
        properties.setEnabled(true);
        properties.setBucket("test-bucket");
        service = new MinioObjectServiceImpl(client, properties);
    }

    @AfterEach
    void stopStorage() throws Exception {
        if (server != null) server.stop(0);
        if (client != null) client.close();
    }

    @Test
    void multipartFileUploadCanBeMeasuredDownloadedPresignedAndDeleted() throws Exception {
        byte[] content = "release-package-content".getBytes(StandardCharsets.UTF_8);
        service.uploadBinaryDefaultBucket("updates/release.zip",
                new MockMultipartFile("file", "release.zip", "application/zip", content), null);

        assertArrayEquals(content, objects.get("/test-bucket/updates/release.zip"));
        assertEquals(content.length, service.contentLengthDefaultBucket("updates/release.zip"));
        try (var downloaded = service.download("test-bucket", "updates/release.zip")) {
            assertArrayEquals(content, downloaded.readAllBytes());
        }
        String url = service.presignedGetUrlDefaultBucket("updates/release.zip", 5);
        assertNotNull(url);
        URI signed = URI.create(url);
        assertEquals("/test-bucket/updates/release.zip", signed.getPath());
        assertTrue(signed.getRawQuery().contains("X-Amz-Signature="));
        assertTrue(signed.getRawQuery().contains("X-Amz-Expires=300"));
        service.deleteObject("test-bucket", "updates/release.zip");
        assertEquals(-1L, service.contentLengthDefaultBucket("updates/release.zip"));
        assertNull(service.presignedGetUrlDefaultBucket("updates/release.zip", 5));
    }

    @Test
    void pluginAndStreamUploadsPreserveBytes() {
        byte[] jar = {80, 75, 3, 4, 1, 2};
        assertEquals("test-bucket", service.uploadJar("plugins/example.jar",
                new MockMultipartFile("file", "example.jar", "application/java-archive", jar)));
        assertArrayEquals(jar, objects.get("/test-bucket/plugins/example.jar"));
        byte[] merged = {0, 1, 2, 3, 4, 5};
        service.uploadFromStream("updates/merged.zip", new ByteArrayInputStream(merged),
                merged.length, "application/octet-stream");
        assertArrayEquals(merged, objects.get("/test-bucket/updates/merged.zip"));
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            exchange.getResponseHeaders().set("x-amz-request-id", "local-fixture");
            exchange.getResponseHeaders().set("ETag", "\"d41d8cd98f00b204e9800998ecf8427e\"");
            exchange.getResponseHeaders().set("Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT");
            if (method.equals("HEAD") && path.equals("/test-bucket")) {
                exchange.sendResponseHeaders(200, -1);
            } else if (method.equals("PUT")) {
                objects.put(path, exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(200, -1);
            } else if (method.equals("DELETE")) {
                objects.remove(path);
                exchange.sendResponseHeaders(204, -1);
            } else if (objects.containsKey(path)) {
                byte[] bytes = objects.get(path);
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(bytes.length));
                exchange.sendResponseHeaders(200, method.equals("HEAD") ? -1 : bytes.length);
                if (!method.equals("HEAD")) exchange.getResponseBody().write(bytes);
            } else {
                byte[] error = "<Error><Code>NoSuchKey</Code><Message>Missing object</Message></Error>"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/xml");
                exchange.sendResponseHeaders(404, method.equals("HEAD") ? -1 : error.length);
                if (!method.equals("HEAD")) exchange.getResponseBody().write(error);
            }
        }
    }
}
