package com.chinacreator.gzcm.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * DuckDB 查询服务（已迁移至 Doris，当前为空壳降级）。
 * 保留接口兼容性，所有操作返回空结果。
 */
@Service
public class DuckDBQueryService {

    private static final Logger log = LoggerFactory.getLogger(DuckDBQueryService.class);

    public DuckDBQueryService() {
        log.info("DuckDBQueryService downgraded (DuckDB migrated to Doris) — all operations are no-ops");
    }

    public List<Map<String, Object>> query(String sql) {
        log.debug("DuckDB query downgraded (no-op): {}", sql.substring(0, Math.min(80, sql.length())));
        return Collections.emptyList();
    }

    public List<String> getTables() {
        return Collections.emptyList();
    }

    public Map<String, Object> exportInfo(String tableName) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("table", tableName);
        info.put("row_count", 0);
        info.put("columns", Collections.emptyList());
        info.put("status", "unavailable");
        info.put("message", "DuckDB migrated to Doris — use /api/v1/task/doris/health instead");
        return info;
    }

    public boolean isHealthy() {
        return false;
    }

    public void registerParquetView(String viewName, String parquetPath) {
        log.info("registerParquetView downgraded (no-op): {} <- {}", viewName, parquetPath);
    }
}
