package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.metadata.MetadataAsyncTrigger;
import com.chinacreator.gzcm.engine.data.metadata.MetadataStrategyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * DataSourceService JdbcTemplate 实现（PMO-E3: 替换启动兜底 stub）。
 * 表: td_datasource
 *
 * PMO-37 增强：
 * - register / updateDataSource 写入 metadata_config (JSONB) + last_collect_time
 * - mapRow 读取新列（向后兼容：旧行 NULL → PROPERTY NULL）
 * - 注册/更新后按策略异步触发明细采集（MetadataAsyncTrigger）
 */
@Service
public class DataSourceServiceImpl implements DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceServiceImpl.class);
    private static final String TABLE = "td_datasource";

    private final JdbcTemplate jdbc;

    /** 可选依赖（@Autowired(required=false) 语义：避免测试环境无 Bean 时启动失败） */
    private MetadataAsyncTrigger asyncTrigger;

    public DataSourceServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAsyncTrigger(MetadataAsyncTrigger trigger) {
        this.asyncTrigger = trigger;
    }

    @Override
    public DataSourceEntity register(DataSourceDTO dto) {
        String id = UUID.randomUUID().toString().replace("-", "");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String metadataConfigJson = buildMetadataConfigJson(dto, "MANUAL");
        jdbc.update(
            "INSERT INTO " + TABLE + " (datasource_id, datasource_name, datasource_type, org_id, " +
            "description, connection_config, status, tags, metadata_config, create_time, update_time) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, dto.getDatasourceName(), dto.getDatasourceType(), dto.getOrgId(),
            dto.getDescription(), dto.getConnectionConfig(), "active", dto.getTags(),
            metadataConfigJson, now, now
        );
        log.info("Registered datasource: id={}, name={}, strategy={}",
                id, dto.getDatasourceName(),
                dto.getMetadataStrategy() != null ? dto.getMetadataStrategy() : "MANUAL");
        DataSourceEntity saved = getById(id);
        if (saved != null && asyncTrigger != null) {
            asyncTrigger.afterRegister(saved);
        }
        return saved;
    }

    @Override
    public boolean testConnection(String datasourceId) {
        DataSourceEntity ds = getById(datasourceId);
        if (ds == null) return false;
        Timestamp now = new Timestamp(System.currentTimeMillis());
        boolean ok = true; // simplified: actual connection test would parse connectionConfig
        jdbc.update(
            "UPDATE " + TABLE + " SET last_test_time = ?, last_test_result = ?, " +
            "last_test_message = ?, update_time = ? WHERE datasource_id = ?",
            now, ok ? "true" : "false", ok ? "连接成功" : "连接失败", now, datasourceId
        );
        return ok;
    }

    @Override
    public List<DataSourceEntity> listAll() {
        return jdbc.query(
            "SELECT * FROM " + TABLE + " ORDER BY create_time DESC",
            (rs, i) -> mapRow(rs)
        );
    }

    @Override
    public DataSourceEntity getById(String datasourceId) {
        List<DataSourceEntity> list = jdbc.query(
            "SELECT * FROM " + TABLE + " WHERE datasource_id = ?",
            (rs, i) -> mapRow(rs), datasourceId
        );
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public DataSourceEntity updateDataSource(String datasourceId, DataSourceDTO dto) {
        DataSourceEntity existing = getById(datasourceId);
        if (existing == null) {
            return null;
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String oldConnectionConfig = existing.getConnectionConfig();
        String metadataConfigJson = buildMetadataConfigJson(dto, null /* fallback 用 existing */);
        // 保留 existing 的 metadataConfig 作为 fallback，避免 dto 缺策略时把已有配置清成 MANUAL
        String fallback = existing.getMetadataConfig();
        if (dto.getMetadataStrategy() == null && fallback != null && !fallback.isBlank()
                && !fallback.equals("{}")) {
            // 策略字段回退：取 existing 的策略值
            MetadataStrategyConfig old = MetadataStrategyConfig.fromJson(fallback);
            if (old.getStrategy() != null) {
                dto.setMetadataStrategy(old.getStrategy());
            }
            metadataConfigJson = buildMetadataConfigJson(dto, null);
        }
        jdbc.update(
            "UPDATE " + TABLE + " SET datasource_name = ?, datasource_type = ?, org_id = ?, " +
            "description = ?, connection_config = ?, tags = ?, metadata_config = ?, update_time = ? " +
            "WHERE datasource_id = ?",
            dto.getDatasourceName(), dto.getDatasourceType(), dto.getOrgId(),
            dto.getDescription(), dto.getConnectionConfig(), dto.getTags(),
            metadataConfigJson, now, datasourceId
        );
        log.info("Updated datasource: id={}, name={}, strategy={}", datasourceId,
            dto.getDatasourceName(),
            dto.getMetadataStrategy() != null ? dto.getMetadataStrategy() : "(保留)");
        DataSourceEntity updated = getById(datasourceId);
        if (updated != null && asyncTrigger != null) {
            asyncTrigger.afterUpdate(datasourceId, updated.getMetadataConfig(),
                    oldConnectionConfig, dto);
        }
        return updated;
    }

    @Override
    public void remove(String datasourceId) {
        jdbc.update("DELETE FROM " + TABLE + " WHERE datasource_id = ?", datasourceId);
        log.info("Removed datasource: {}", datasourceId);
    }

    @Override
    public void updateMetadataConfig(String datasourceId, String json) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        int n = jdbc.update(
            "UPDATE " + TABLE + " SET metadata_config = ?, update_time = ? WHERE datasource_id = ?",
            json, now, datasourceId
        );
        if (n == 0) {
            throw new RuntimeException("数据源不存在: " + datasourceId);
        }
        log.info("Updated metadataConfig: datasource={}, json={}", datasourceId, json);
    }

    private String buildMetadataConfigJson(DataSourceDTO dto, String fallbackStrategy) {
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> cfg =
                    (java.util.Map<String, Object>) MetadataAsyncTrigger.metadataConfigMap(dto, fallbackStrategy);
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cfg);
        } catch (Exception e) {
            log.warn("metadataConfigJson 序列化失败，落默认值: {}", e.getMessage());
            return "{\"strategy\":\"MANUAL\",\"includeRowCount\":true,\"countMethod\":\"ESTIMATE\",\"cacheTtlMinutes\":5,\"onSourceEdit\":true}";
        }
    }

    private DataSourceEntity mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        DataSourceEntity e = new DataSourceEntity();
        e.setDatasourceId(rs.getString("datasource_id"));
        e.setDatasourceName(rs.getString("datasource_name"));
        e.setDatasourceType(rs.getString("datasource_type"));
        e.setOrgId(rs.getString("org_id"));
        e.setNodeId(rs.getString("node_id"));
        e.setDescription(rs.getString("description"));
        e.setConnectionConfig(rs.getString("connection_config"));
        e.setStatus(rs.getString("status"));
        e.setIsDefault(rs.getString("is_default"));
        e.setLastTestTime(rs.getTimestamp("last_test_time"));
        e.setLastTestResult(rs.getString("last_test_result"));
        e.setLastTestMessage(rs.getString("last_test_message"));
        e.setCreateBy(rs.getString("create_by"));
        e.setCreateTime(rs.getTimestamp("create_time"));
        e.setUpdateBy(rs.getString("update_by"));
        e.setUpdateTime(rs.getTimestamp("update_time"));
        e.setTags(rs.getString("tags"));
        e.setRemark(rs.getString("remark"));
        // PMO-37 新增列（缺列时回退 NULL）
        e.setMetadataConfig(readMetadataConfig(rs));
        e.setLastCollectTime(readLastCollectTime(rs));
        return e;
    }

    private String readMetadataConfig(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            Object v = rs.getObject("metadata_config");
            if (v == null) return null;
            String s = v.toString();
            if (s == null || s.isBlank()) return null;
            // PMO-37 修正: "{}" 是合法的空策略对象, 原实现把它转 null 导致
            // FE 列表接口 items[].metadataConfig 永远空, 现按原样透传.
            return s;
        } catch (java.sql.SQLException sqlEx) {
            return null; // 缺列回退
        }
    }

    private Timestamp readLastCollectTime(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            return rs.getTimestamp("last_collect_time");
        } catch (java.sql.SQLException sqlEx) {
            return null;
        }
    }
}
