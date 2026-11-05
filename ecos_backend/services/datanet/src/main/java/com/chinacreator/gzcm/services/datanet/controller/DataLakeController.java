package com.chinacreator.gzcm.services.datanet.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.IObjectStorageService;
import com.chinacreator.gzcm.services.datanet.service.DataLakeExportService;
import com.chinacreator.gzcm.runtime.access.olap.DuckDBQueryService;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * DataLake 嵌入式数据湖 API 控制器（P3-A 从 gateway 迁至 datanet：/api/datalake）。
 * 提供数据导出、OLAP 查询、数据集管理和健康检查端点。
 *
 * <p>DI-2 #147EN：查询回执（sql/row_count/data）与健康检查（duckdb/minio/
 * [minio_http]/datalake_version）改 record；行数据与探针结果仍为 runtime 原样结构
 * （{@code Object} 透传，属动态 payload 豁免）。</p>
 *
 * @author ecos-factory
 * @since PMO-49 P3-A
 */
@RestController
@RequestMapping("/api/datalake")
public class DataLakeController {

    /** 查询回执：3 键（行数据原样透传）。 */
    @JsonPropertyOrder({"sql", "row_count", "data"})
    public record DataLakeQueryVO(@JsonProperty("sql") String sql,
                                  @JsonProperty("row_count") int rowCount,
                                  @JsonProperty("data") Object data) {
    }

    /** 健康检查：duckdb/minio/version 恒在，minio_http 仅探针触发时出现。 */
    @JsonPropertyOrder({"duckdb", "minio", "minio_http", "datalake_version"})
    public record DataLakeHealthVO(@JsonProperty("duckdb") Object duckdb,
                                   @JsonProperty("minio") Object minio,
                                   @JsonInclude(JsonInclude.Include.NON_NULL)
                                   @JsonProperty("minio_http") Object minioHttp,
                                   @JsonProperty("datalake_version") String datalakeVersion) {
    }

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
    public ApiResponse<DataLakeQueryVO> query(@RequestBody Map<String, Object> body) {
        String sql = (String) body.get("sql");
        if (sql == null || sql.isBlank()) {
            return ApiResponse.badRequest("参数 'sql' 不能为空");
        }
        log.info("DataLake query: {}", sql);
        try {
            List<Map<String, Object>> results = duckDB.query(sql);
            return ApiResponse.success(new DataLakeQueryVO(sql, results.size(), results));
        } catch (Exception e) {
            log.error("DataLake query failed", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/tables")
    public ApiResponse<List<Map<String, Object>>> listTables() {
        List<Map<String, Object>> datasets = exportService.listExportedDatasets();
        return ApiResponse.success(datasets);
    }

    @GetMapping("/health")
    public ApiResponse<DataLakeHealthVO> health() {
        Object duckdb = Map.of("status", "DOWN", "note", "migrated to Doris");
        Map<String, Object> minioCheck = minioStorage.healthCheck();
        Object minioHttp = null;
        try {
            if ("DOWN".equals(minioCheck.get("status"))) {
                try {
                    java.net.URL minioUrl = new java.net.URL(minioStorage.getEndpoint() + "/minio/health/live");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) minioUrl.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    int code = conn.getResponseCode();
                    minioHttp = Map.of("status", code == 200 ? "UP" : "DOWN", "code", code);
                    conn.disconnect();
                } catch (Exception httpEx) {
                    minioHttp = Map.of("status", "DOWN", "message", httpEx.getMessage());
                }
            }
        } catch (Exception e) {
            minioHttp = Map.of("status", "DOWN", "message", e.getMessage());
        }
        return ApiResponse.success(new DataLakeHealthVO(duckdb, minioCheck, minioHttp, "P2-15"));
    }

    @GetMapping("/minio/objects")
    public ApiResponse<List<Map<String, Object>>> listMinioObjects(
            @RequestParam(defaultValue = "") String prefix) {
        List<Map<String, Object>> objects = minioStorage.listObjects(prefix);
        return ApiResponse.success(objects);
    }
}
