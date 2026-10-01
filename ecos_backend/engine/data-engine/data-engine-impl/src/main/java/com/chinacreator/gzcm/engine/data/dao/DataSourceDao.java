package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Data-access for the td_datasource table: registration, reads (with password
 * ciphertext column) and update/delete. Row-mapping into {@link DataSourceEntity}
 * lives here; response-level masking is the caller's concern.
 */
@Repository
public class DataSourceDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "td_datasource";

    public void register(String id, String name, String type, String orgId, String description,
                         String connectionConfig, String tags, String metadataConfig,
                         Timestamp now, String passwordEnc) {
        jdbcTemplate.update(
            "INSERT INTO " + TABLE + " (datasource_id, datasource_name, datasource_type, org_id, " +
            "description, connection_config, status, tags, metadata_config, create_time, update_time, password_enc) " +
            "VALUES (?,?,?,?,?,?, 'active', ?, ?, ?, ?, ?)",
            id, name, type, orgId,
            description, connectionConfig, tags,
            metadataConfig, now, now, passwordEnc
        );
    }

    public void updateLastTest(String datasourceId, Timestamp now, boolean ok, String message) {
        jdbcTemplate.update(
            "UPDATE " + TABLE + " SET last_test_time = ?, last_test_result = ?, " +
            "last_test_message = ?, update_time = ? WHERE datasource_id = ?",
            now, ok ? "true" : "false", message, now, datasourceId
        );
    }

    public List<DataSourceEntity> listAll() {
        return jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " ORDER BY create_time DESC",
            (rs, rowNum) -> mapRow(rs));
    }

    public List<DataSourceEntity> findByDatasourceId(String datasourceId) {
        return jdbcTemplate.query(
            "SELECT * FROM " + TABLE + " WHERE datasource_id = ?",
            (rs, rowNum) -> mapRow(rs), datasourceId);
    }

    public void updateDataSource(String datasourceId, String name, String type, String orgId,
                                 String description, String connectionConfig, String tags,
                                 String metadataConfig, String passwordEnc, Timestamp now) {
        jdbcTemplate.update(
            "UPDATE " + TABLE + " SET datasource_name = ?, datasource_type = ?, org_id = ?, " +
            "description = ?, connection_config = ?, tags = ?, metadata_config = ?, " +
            "password_enc = ?, update_time = ? WHERE datasource_id = ?",
            name, type, orgId,
            description, connectionConfig, tags,
            metadataConfig, passwordEnc, now, datasourceId
        );
    }

    public void remove(String datasourceId) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE datasource_id = ?", datasourceId);
    }

    public int updateMetadataConfig(String datasourceId, String json, Timestamp now) {
        return jdbcTemplate.update(
            "UPDATE " + TABLE + " SET metadata_config = ?, update_time = ? WHERE datasource_id = ?",
            json, now, datasourceId
        );
    }

    public void persistLastTest(String datasourceId, Timestamp now, String status, String message) {
        jdbcTemplate.update(
            "UPDATE " + TABLE + " SET last_test_time=?, last_test_result=?, last_test_message=?, update_time=? WHERE datasource_id=?",
            now, status, message, now, datasourceId);
    }

    // ──────────────────────────────────────────────────────────────────
    // Row mapping (moved from DataSourceServiceImpl)
    // ──────────────────────────────────────────────────────────────────

    private static DataSourceEntity mapRow(ResultSet rs) throws SQLException {
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
        // PMO-49: 密码密文列（decryption 回填跑 connectionConfig 用；公开 getById 前脱敏）
        e.setPasswordEncrypted(readPasswordEnc(rs));
        return e;
    }

    private static String readPasswordEnc(ResultSet rs) {
        try {
            return rs.getString("password_enc");
        } catch (SQLException sqlEx) {
            return null; // 缺列回退（兼容未迁移库）
        }
    }

    private static String readMetadataConfig(ResultSet rs) {
        try {
            Object v = rs.getObject("metadata_config");
            if (v == null) return null;
            String s = v.toString();
            if (s == null || s.isBlank()) return null;
            return s;
        } catch (SQLException sqlEx) {
            return null; // 缺列回退
        }
    }

    private static Timestamp readLastCollectTime(ResultSet rs) {
        try {
            return rs.getTimestamp("last_collect_time");
        } catch (SQLException sqlEx) {
            return null;
        }
    }
}
