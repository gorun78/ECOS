package com.chinacreator.gzcm.runtime.access.storage;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P3-1 MinIO 对象存储服务。
 * <p>
 * 封装 MinIO SDK 的 putObject/getObject/listObjects 操作：
 * <ul>
 *   <li>putObject: 上传文件到 MinIO bucket</li>
 *   <li>getObject: 从 MinIO bucket 下载文件</li>
 *   <li>listObjects: 列出 bucket 中的对象</li>
 * </ul>
 * <p>
 * MinIO endpoint 从 sys_config 表或默认配置读取。
 */
@Service
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private static final String DEFAULT_ENDPOINT = "http://localhost:9000";
    private static final String DEFAULT_ACCESS_KEY = "minioadmin";
    private static final String DEFAULT_SECRET_KEY = "minioadmin";
    private static final String DEFAULT_BUCKET = "ecos-datalake";

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;

    /** yml 配置注入（minio.endpoint 等），优先于环境变量与默认值；为空时走兜底 */
    @Value("${minio.endpoint:}")
    private String cfgEndpoint;
    @Value("${minio.access-key:}")
    private String cfgAccessKey;
    @Value("${minio.secret-key:}")
    private String cfgSecretKey;
    @Value("${minio.bucket:}")
    private String cfgBucket;

    private volatile MinioClient client;
    private volatile boolean initialized = false;

    public MinioStorageService() {
        // 从环境变量读取，fallback 到默认值（yml 配置在 @Value 注入后优先）
        this.endpoint = System.getenv().getOrDefault("MINIO_ENDPOINT", DEFAULT_ENDPOINT);
        this.accessKey = System.getenv().getOrDefault("MINIO_ACCESS_KEY", DEFAULT_ACCESS_KEY);
        this.secretKey = System.getenv().getOrDefault("MINIO_SECRET_KEY", DEFAULT_SECRET_KEY);
    }

    /** 生效端点：yml 配置 > 环境变量 > 默认值 */
    private String effectiveEndpoint() {
        return isBlank(cfgEndpoint) ? endpoint : cfgEndpoint;
    }

    /** 生效 access key：yml 配置 > 环境变量 > 默认值 */
    private String effectiveAccessKey() {
        return isBlank(cfgAccessKey) ? accessKey : cfgAccessKey;
    }

    /** 生效 secret key：yml 配置 > 环境变量 > 默认值 */
    private String effectiveSecretKey() {
        return isBlank(cfgSecretKey) ? secretKey : cfgSecretKey;
    }

    /** 生效 bucket：yml 配置 > 默认值 */
    private String effectiveBucket() {
        return isBlank(cfgBucket) ? DEFAULT_BUCKET : cfgBucket;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 延迟初始化 MinioClient。
     */
    private MinioClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    try {
                        client = MinioClient.builder()
                                .endpoint(effectiveEndpoint())
                                .credentials(effectiveAccessKey(), effectiveSecretKey())
                                .build();
                        initialized = true;
                        log.info("P3-1 MinioClient initialized: endpoint={}", effectiveEndpoint());
                    } catch (Exception e) {
                        log.error("P3-1 Failed to create MinioClient: {}", e.getMessage());
                        throw new RuntimeException("MinIO client init failed", e);
                    }
                }
            }
        }
        return client;
    }

    /**
     * 确保 bucket 存在。
     */
    public void ensureBucket() {
        try {
            MinioClient mc = getClient();
            String bucket = effectiveBucket();
            boolean found = mc.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                mc.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("P3-1 MinIO bucket '{}' created", bucket);
            } else {
                log.debug("P3-1 MinIO bucket '{}' already exists", bucket);
            }
        } catch (Exception e) {
            log.warn("P3-1 Failed to ensure MinIO bucket '{}': {}", effectiveBucket(), e.getMessage());
        }
    }

    /**
     * 上传本地文件到 MinIO。
     *
     * @param objectName MinIO 对象名（如 "datalake/ecos_audit_log.parquet"）
     * @param filePath   本地文件路径
     * @return 上传结果
     */
    public Map<String, Object> putObject(String objectName, Path filePath) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("object", objectName);

        try {
            ensureBucket();

            MinioClient mc = getClient();
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";

            mc.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(effectiveBucket())
                            .object(objectName)
                            .filename(filePath.toString())
                            .contentType(contentType)
                            .build()
            );

            long fileSize = Files.size(filePath);
            result.put("status", "success");
            result.put("size_bytes", fileSize);
            result.put("bucket", effectiveBucket());

            log.info("P3-1 Uploaded to MinIO: {}/{} ({} bytes)", effectiveBucket(), objectName, fileSize);

        } catch (Exception e) {
            log.error("P3-1 Failed to upload object '{}': {}", objectName, e.getMessage());
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * 上传字节数据到 MinIO。
     */
    public Map<String, Object> putObject(String objectName, byte[] data, String contentType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("object", objectName);

        try {
            ensureBucket();

            MinioClient mc = getClient();
            mc.putObject(
                    PutObjectArgs.builder()
                            .bucket(effectiveBucket())
                            .object(objectName)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );

            result.put("status", "success");
            result.put("size_bytes", data.length);
            result.put("bucket", effectiveBucket());

            log.info("P3-1 Uploaded to MinIO (bytes): {}/{} ({} bytes)", effectiveBucket(), objectName, data.length);

        } catch (Exception e) {
            log.error("P3-1 Failed to upload object '{}': {}", objectName, e.getMessage());
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * 从 MinIO 下载对象为字节数组。
     */
    public byte[] getObject(String objectName) {
        try {
            MinioClient mc = getClient();
            try (InputStream stream = mc.getObject(
                    GetObjectArgs.builder()
                            .bucket(effectiveBucket())
                            .object(objectName)
                            .build())) {
                return stream.readAllBytes();
            }
        } catch (Exception e) {
            log.error("P3-1 Failed to get object '{}': {}", objectName, e.getMessage());
            throw new RuntimeException("MinIO getObject error: " + e.getMessage(), e);
        }
    }

    /**
     * 列出 bucket 中的对象。
     */
    public List<Map<String, Object>> listObjects(String prefix) {
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            MinioClient mc = getClient();
            Iterable<Result<Item>> results = mc.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(effectiveBucket())
                            .prefix(prefix != null ? prefix : "")
                            .recursive(true)
                            .build());

            for (Result<Item> result : results) {
                Item item = result.get();
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("name", item.objectName());
                obj.put("size", item.size());
                obj.put("lastModified", item.lastModified() != null ? item.lastModified().toString() : null);
                obj.put("isDir", item.isDir());
                items.add(obj);
            }
        } catch (Exception e) {
            log.error("P3-1 Failed to list objects (prefix='{}'): {}", prefix, e.getMessage());
        }
        return items;
    }

    /**
     * 检查 MinIO 服务健康状态。
     */
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        try {
            MinioClient mc = getClient();
            String bucket = effectiveBucket();
            boolean bucketExists = mc.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            health.put("endpoint", effectiveEndpoint());
            health.put("bucket", bucket);
            health.put("status", bucketExists ? "UP" : "DEGRADED");
            health.put("initialized", initialized);
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("message", e.getMessage());
            health.put("initialized", initialized);
        }
        return health;
    }

    public String getBucketName() {
        return effectiveBucket();
    }

    public String getEndpoint() {
        return effectiveEndpoint();
    }

    public String getAccessKey() {
        return effectiveAccessKey();
    }

    public boolean isInitialized() {
        return initialized;
    }
}
