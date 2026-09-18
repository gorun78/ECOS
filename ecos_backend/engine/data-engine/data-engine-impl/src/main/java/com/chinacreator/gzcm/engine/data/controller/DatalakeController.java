package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据湖（MinIO 近源库）初始化与状态 REST API。
 * <p>
 * 端点：
 * <ul>
 *   <li>GET  /api/v1/datanet/datalake/status   — 健康状态 + 配置（secretKey 脱敏）</li>
 *   <li>POST /api/v1/datanet/datalake/init     — 初始化：确保 bucket 存在</li>
 *   <li>GET  /api/v1/datanet/datalake/objects  — 列出数据湖对象（prefix 过滤，如 datalake/）</li>
 * </ul>
 * <p>对应需求：设置好数据湖 MinIO 初始参数，并作为数据采集（采集型管道）的默认近源库目标。
 */
@RestController
@RequestMapping("/api/v1/datanet/datalake")
public class DatalakeController {

    private final MinioStorageService minioStorageService;

    public DatalakeController(MinioStorageService minioStorageService) {
        this.minioStorageService = minioStorageService;
    }

    /**
     * 数据湖健康状态（secretKey 脱敏，不暴露凭据）。
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> health = minioStorageService.healthCheck();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("endpoint", health.get("endpoint"));
        data.put("bucket", health.get("bucket"));
        data.put("status", health.get("status"));
        data.put("initialized", health.get("initialized"));
        data.put("accessKey", maskSecret(String.valueOf(minioStorageService.getAccessKey())));
        if (health.containsKey("message")) {
            data.put("message", health.get("message"));
        }
        return ApiResponse.success(data);
    }

    /**
     * 初始化数据湖：确保 bucket 存在（幂等）。
     */
    @PostMapping("/init")
    public ApiResponse<Map<String, Object>> init() {
        minioStorageService.ensureBucket();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bucket", minioStorageService.getBucketName());
        data.put("endpoint", minioStorageService.getEndpoint());
        data.put("status", minioStorageService.healthCheck().get("status"));
        return ApiResponse.success(data);
    }

    /**
     * 列出数据湖对象（默认列全部，可用 prefix 过滤）。
     */
    @GetMapping("/objects")
    public ApiResponse<Map<String, Object>> listObjects(@RequestParam(defaultValue = "") String prefix) {
        List<Map<String, Object>> items = minioStorageService.listObjects(prefix);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bucket", minioStorageService.getBucketName());
        data.put("prefix", prefix);
        data.put("items", items);
        data.put("total", items.size());
        return ApiResponse.success(data);
    }

    /** 脱敏：保留前 3 位，其余打码。 */
    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 3) {
            return "***";
        }
        return value.substring(0, 3) + "***";
    }
}
