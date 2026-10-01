package com.chinacreator.gzcm.engine.security.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 数据资产打级 Listener 用策略 DAO（R2-B3b 数据访问收口；只搬不改语义）。
 *
 * <p>表：{@code ecos_cls_policy} / {@code ecos_rls_policy}（裸表名，与存量 SQL 一致）。
 * 供 {@code DataSecurityLevelEventListener} 消费打级事件后双轨写入策略
 * （resource_id 优先 + table_name 兜底，V154 IR03 只加不删）。</p>
 */
@Repository
public class DataSecurityLevelDao extends JdbcTemplateDaoSupport {

    /** 查找已存在策略 id（enabled + resource_id 优先 / table_name 兜底）。 */
    public String findExistingPolicy(String policyTable, String resourceId, String tableName) {
        String sql = "SELECT id FROM " + policyTable +
                " WHERE enabled = true " +
                " AND (resource_id = ? OR table_name = ?) LIMIT 1";
        List<String> ids = jdbcTemplate.queryForList(sql, String.class, resourceId, tableName);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** CLS 存量策略覆盖式更新（仅写老列，避免迁移前缺列造成 SQL 报错）。 */
    public int updateClsPolicy(String blockedColsJson, String description, String createdBy, String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_cls_policy SET blocked_cols = ?, description = ?, created_by = ? " +
            "WHERE id = ?",
            blockedColsJson, description, createdBy, id);
    }

    /** CLS 策略新建（policy_name = 'asset-' || assetId）。 */
    public int insertClsPolicy(String id, String assetId, String tableName, String resourceId,
                               String blockedColsJson, String description, String createdBy) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_cls_policy (" +
            "  id, policy_name, table_name, resource_id, " +
            "  visible_cols, blocked_cols, description, " +
            "  enabled, priority, created_by" +
            ") VALUES (?, 'asset-' || ?, ?, ?, ?, ?, ?, true, 10, ?)",
            id, assetId, tableName, resourceId,
            "[]", blockedColsJson, description, createdBy);
    }

    /** RLS 存量策略覆盖式更新。 */
    public int updateRlsPolicy(String filterExpr, String description, String createdBy, String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_rls_policy SET filter_expr = ?, description = ?, created_by = ? WHERE id = ?",
            filterExpr, description, createdBy, id);
    }

    /** RLS 策略新建（policy_name = 'asset-' || assetId）。 */
    public int insertRlsPolicy(String id, String assetId, String tableName, String resourceId,
                               String filterExpr, String description, String createdBy) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_rls_policy (" +
            "  id, policy_name, table_name, resource_id, filter_expr, " +
            "  description, enabled, priority, created_by" +
            ") VALUES (?, 'asset-' || ?, ?, ?, ?, ?, true, 10, ?)",
            id, assetId, tableName, resourceId, filterExpr, description, createdBy);
    }
}
