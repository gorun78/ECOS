package com.chinacreator.gzcm.workspace.service;

import com.chinacreator.gzcm.common.service.IObjectStorageService;
import io.minio.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * A3 MinIO 对象存储实现（企业版 / 旗舰版）。
 * <p>
 * 从 gateway 的 MinioStorageService 迁出核心 put/get/delete 逻辑，
 * 通过 {@link IObjectStorageService} 接口暴露。
 * <p>
 * 激活条件：Spring profile {@code enterprise} 或 {@code flagship}。
 */
@Service
public class MinioObjectStorageService implements IObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorageService.class);

    private static final String DEFAULT_ENDPOINT = "http://localhost:9000";
    private static final String DEFAULT_ACCESS_KEY = "minioadmin";
    private static final String DEFAULT_SECRET_KEY = "minioadmin";
    private static final String BUCKET_NAME = "ecos-datalake";

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;

    private volatile MinioClient client;

    public MinioObjectStorageService() {
        this.endpoint = System.getenv().getOrDefault("MINIO_ENDPOINT", DEFAULT_ENDPOINT);
        this.accessKey = System.getenv().getOrDefault("MINIO_ACCESS_KEY", DEFAULT_ACCESS_KEY);
        this.secretKey = System.getenv().getOrDefault("MINIO_SECRET_KEY", DEFAULT_SECRET_KEY);
    }

    private MinioClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = MinioClient.builder()
                            .endpoint(endpoint)
                            .credentials(accessKey, secretKey)
                            .build();
                    log.info("A3 MinioClient initialized: endpoint={}", endpoint);
                }
            }
        }
        return client;
    }

    private void ensureBucket() {
        try {
            MinioClient mc = getClient();
            boolean found = mc.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build());
            if (!found) {
                mc.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
                log.info("A3 MinIO bucket '{}' created", BUCKET_NAME);
            }
        } catch (Exception e) {
            log.warn("A3 Failed to ensure MinIO bucket '{}': {}", BUCKET_NAME, e.getMessage());
        }
    }

    @Override
    public String putObject(String key, byte[] data, String contentType) {
        try {
            ensureBucket();
            MinioClient mc = getClient();
            mc.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(key)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );
            log.info("A3 MinIO putObject: {}/{} ({} bytes)", BUCKET_NAME, key, data.length);
            return key;
        } catch (Exception e) {
            log.error("A3 MinIO putObject '{}' failed: {}", key, e.getMessage());
            throw new RuntimeException("MinIO putObject error: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] getObject(String key) {
        try {
            MinioClient mc = getClient();
            try (InputStream stream = mc.getObject(
                    GetObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(key)
                            .build())) {
                return stream.readAllBytes();
            }
        } catch (Exception e) {
            log.error("A3 MinIO getObject '{}' failed: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public void deleteObject(String key) {
        try {
            MinioClient mc = getClient();
            mc.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(key)
                            .build()
            );
            log.info("A3 MinIO deleteObject: {}/{}", BUCKET_NAME, key);
        } catch (Exception e) {
            log.error("A3 MinIO deleteObject '{}' failed: {}", key, e.getMessage());
            throw new RuntimeException("MinIO deleteObject error: " + e.getMessage(), e);
        }
    }
}
