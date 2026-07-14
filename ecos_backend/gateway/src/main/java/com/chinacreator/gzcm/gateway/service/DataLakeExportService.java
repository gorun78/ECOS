package com.chinacreator.gzcm.gateway.service;

import com.chinacreator.gzcm.common.service.IObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * DataLake 导出服务。
 * DuckDB 已迁移至 Doris，exportTable 降级为空操作。
 */
@Service
public class DataLakeExportService {

    private static final Logger log = LoggerFactory.getLogger(DataLakeExportService.class);
    private static final String EXPORT_DIR = "./data/datalake";

    private final JdbcTemplate pgJdbc;
    private final DuckDBQueryService duckDB;
    private final MinioStorageService minioStorage;
    private final IObjectStorageService objectStorage;

    public DataLakeExportService(JdbcTemplate pgJdbc, DuckDBQueryService duckDB,
                                  MinioStorageService minioStorage,
                                  @Qualifier("minioObjectStorageService") IObjectStorageService objectStorage) {
        this.pgJdbc = pgJdbc;
        this.duckDB = duckDB;
        this.minioStorage = minioStorage;
        this.objectStorage = objectStorage;
    }

    /**
     * P2-15 导出 PG 表到本地 Parquet 文件并上传到 MinIO。
     * 使用分页读取 (每批 10000 行) 避免大表 OOM。
     *
     * @param tableName 源 PG 表名
     * @return 导出结果信息
     */
    public Map<String, Object> exportTable(String tableName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", tableName);
        result.put("format", "parquet");
        result.put("status", "unavailable");
        result.put("message", "DuckDB migrated to Doris — export via DuckDB is disabled");
        log.info("DataLake export downgraded (DuckDB→Doris): table={}", tableName);
        return result;
    }

    /**
     * 获取已导出的数据集列表（包括 MinIO 远程列表）。
     */
    public List<Map<String, Object>> listExportedDatasets() {
        List<Map<String, Object>> datasets = new ArrayList<>();
        Path dir = Paths.get(EXPORT_DIR);
        if (!Files.exists(dir)) {
            // 回退到 MinIO 列表
            return listMinioDatasets();
        }

        try {
            Files.list(dir)
                    .filter(p -> p.toString().endsWith(".parquet"))
                    .forEach(p -> {
                        Map<String, Object> ds = new LinkedHashMap<>();
                        String name = p.getFileName().toString().replace(".parquet", "");
                        ds.put("table", name);
                        ds.put("format", "parquet");
                        try {
                            ds.put("file_size_mb", String.format("%.2f", Files.size(p) / 1048576.0));
                        } catch (IOException ignored) {
                            ds.put("file_size_mb", "unknown");
                        }
                        ds.put("path", p.toAbsolutePath().toString());
                        datasets.add(ds);
                    });
        } catch (IOException e) {
            log.error("Failed to list datasets: {}", e.getMessage());
        }

        // 补充 MinIO 信息
        if (!datasets.isEmpty()) {
            try {
                List<Map<String, Object>> minioObjects = minioStorage.listObjects("datalake/");
                for (Map<String, Object> ds : datasets) {
                    String tableName = (String) ds.get("table");
                    ds.put("minio_uploaded", minioObjects.stream()
                            .anyMatch(o -> o.get("name").toString().contains(tableName)));
                }
            } catch (Exception e) {
                log.debug("P3-1 MinIO list for datasets skipped: {}", e.getMessage());
            }
        }

        return datasets;
    }

    /**
     * 从 MinIO 列出已导出的数据集（作为本地目录的补充/回退）。
     */
    private List<Map<String, Object>> listMinioDatasets() {
        List<Map<String, Object>> datasets = new ArrayList<>();
        try {
            List<Map<String, Object>> objects = minioStorage.listObjects("datalake/");
            for (Map<String, Object> obj : objects) {
                String name = (String) obj.get("name");
                if (name != null && name.endsWith(".parquet")) {
                    Map<String, Object> ds = new LinkedHashMap<>();
                    String tableName = name.replace("datalake/", "").replace(".parquet", "");
                    ds.put("table", tableName);
                    ds.put("format", "parquet");
                    ds.put("path", "s3://" + minioStorage.getBucketName() + "/" + name);
                    Long size = (Long) obj.get("size");
                    ds.put("file_size_mb", size != null ? String.format("%.2f", size / 1048576.0) : "unknown");
                    ds.put("source", "minio");
                    datasets.add(ds);
                }
            }
        } catch (Exception e) {
            log.warn("P3-1 Failed to list MinIO datasets: {}", e.getMessage());
        }
        return datasets;
    }

}
