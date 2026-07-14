package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.IObjectStorageService;
import com.chinacreator.gzcm.gateway.service.DataLakeExportService;
import com.chinacreator.gzcm.gateway.service.DuckDBQueryService;
import com.chinacreator.gzcm.gateway.service.MinioStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * DataLake 嵌入式数据湖 API 控制器。
 * 提供数据导出、OLAP 查询、数据集管理和健康检查端点。
 */
@RestController
@RequestMapping("/api/datalake")
@Tag(name = "DataLake", description = "嵌入式数据湖 — 数据导出、OLAP 查询、数据集管理")
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

    @Operation(summary = "导出数据表", description = "将 PostgreSQL 业务表导出为 Parquet 文件并上传到 MinIO")
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

    @Operation(summary = "OLAP 查询", description = "通过 DuckDB 执行 OLAP 查询")
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

    @Operation(summary = "数据集列表", description = "获取已导出的数据集列表")
    @GetMapping("/tables")
    public ApiResponse<List<Map<String, Object>>> listTables() {
        List<Map<String, Object>> datasets = exportService.listExportedDatasets();
        return ApiResponse.success(datasets);
    }

    @Operation(summary = "健康检查", description = "检查 DuckDB 和 MinIO 的健康状态")
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

    @Operation(summary = "MinIO 对象列表", description = "列出 MinIO 存储桶中的对象")
    @GetMapping("/minio/objects")
    public ApiResponse<List<Map<String, Object>>> listMinioObjects(
            @RequestParam(defaultValue = "") String prefix) {
        List<Map<String, Object>> objects = minioStorage.listObjects(prefix);
        return ApiResponse.success(objects);
    }
}
