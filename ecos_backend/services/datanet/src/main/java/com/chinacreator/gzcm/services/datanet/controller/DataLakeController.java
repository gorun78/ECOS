package com.chinacreator.gzcm.services.datanet.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.IObjectStorageService;
import com.chinacreator.gzcm.services.datanet.service.DataLakeExportService;
import com.chinacreator.gzcm.runtime.access.olap.DuckDBQueryService;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * DataLake 嵌入式数据湖 API 控制器（P3-A 从 gateway 迁至 datanet：/api/datalake）。
 * 提供数据导出、OLAP 查询、数据集管理和健康检查端点。
 *
 * @author ecos-factory
 * @since PMO-49 P3-A
 */
@RestController
@RequestMapping("/api/datalake")
public class DataLakeController {

    private static final Logger log = LoggerFactory.getLogger(DataLakeController.class);
    private final DuckDBQueryService duckDB;
    private final DataLakeExportService exportService;
    private final MinioStorageService minioStorage;
    private final IObjectStorageService objectStorage;

    public DataLakeController(DuckDBQueryService duckDB, DataLakeExportService exportService,
                               MinioStorageService minioStorage,
                               @Qualifier("minioObjectStorageService") IObjectStorageService objectStorage) {
        this.duckDB = duckDB;
        this.exportService = exportService;
        this.minioStorage = minioStorage;
        this.objectStorage = objectStorage;
    }

    @PostMapping("/export")
    public ApiResponse<Map<String, Object>> exportTable(@RequestBody Map<String, Object> body) {
        String table = (String) body.get("table");
        if (table == null || table.isBlank()) {
            return ApiResponse.badRequest("参数 'table' 不能为空");
        }
        log.info("DataLake export request: table={}", table);
        Map<String, Object> result = exportService.exportTable(table);
        if ("error".equals(result.get("status"))) {
            return ApiResponse.internalError((String) result.getOrDefault("message", "导出失败"));
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/query")
    public ApiResponse<?> query(@RequestBody Map<String, Object> body) {
        String sql = (String) body.get("sql");
        if (sql == null || sql.isBlank()) {
            return ApiResponse.badRequest("参数 'sql' 不能为空");
        }
        log.info("DataLake query: {}", sql);
        try {
            List<Map<String, Object>> results = duckDB.query(sql);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("sql", sql);
            resp.put("row_count", results.size());
            resp.put("data", results);
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("DataLake query failed: {}", e.getMessage());
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/tables")
    public ApiResponse<List<Map<String, Object>>> listTables() {
        List<Map<String, Object>> datasets = exportService.listExportedDatasets();
        return ApiResponse.success(datasets);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("duckdb", Map.of("status", "DOWN", "note", "migrated to Doris"));
        health.put("minio", minioStorage.healthCheck());
        try {
            Map<String, Object> minioHealth = minioStorage.healthCheck();
            if ("DOWN".equals(minioHealth.get("status"))) {
                try {
                    java.net.URL minioUrl = new java.net.URL(minioStorage.getEndpoint() + "/minio/health/live");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) minioUrl.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    int code = conn.getResponseCode();
                    health.put("minio_http", Map.of("status", code == 200 ? "UP" : "DOWN", "code", code));
                    conn.disconnect();
                } catch (Exception httpEx) {
                    health.put("minio_http", Map.of("status", "DOWN", "message", httpEx.getMessage()));
                }
            }
        } catch (Exception e) {
            health.put("minio_http", Map.of("status", "DOWN", "message", e.getMessage()));
        }
        health.put("datalake_version", "P2-15");
        return ApiResponse.success(health);
    }

    @GetMapping("/minio/objects")
    public ApiResponse<List<Map<String, Object>>> listMinioObjects(
            @RequestParam(defaultValue = "") String prefix) {
        List<Map<String, Object>> objects = minioStorage.listObjects(prefix);
        return ApiResponse.success(objects);
    }
}
