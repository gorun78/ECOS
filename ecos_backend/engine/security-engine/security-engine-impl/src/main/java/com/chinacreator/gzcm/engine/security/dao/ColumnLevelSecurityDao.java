package com.chinacreator.gzcm.engine.security.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 列级安全（CLS）策略 DAO（R2-B3b 数据访问收口；只搬不改语义）。
 *
 * <p>表：{@code ecos_cls_policy}（+ 角色子查询 {@code TD_USER_ROLE}）。
 * 供 {@code ColumnLevelSecurityServiceImpl} 使用；fail-closed 判定留在 service。</p>
 */
@Repository
public class ColumnLevelSecurityDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_cls_policy";

    /**
     * 命中策略查询（table_name OR resource_id 双轨 + user/role 匹配，priority 升序）。
     *
     * <p>#142/B4-3a：角色子查询的 {@code "ROLE_ID"/"USER_ID"} 引号大写形态在 dev PG 上
     * 报 {@code ERROR: column "ROLE_ID" does not exist}（与 {@link RowLevelSecurityDao} 同缺陷、
     * 同修法、同 DDL 单源依据）。CLS 侧后果更硬：异常被 {@code getColumns} 的 catch 转成
     * {@code visibleColumns=[]}（fail-closed 全拒绝）⇒ 列级策略<b>永远命中不到</b>，
     * 非财务角色的"隐藏时薪/金额列"与财务角色的"可见"两种预期同时落空。
     * 形态由 {@code RoleResolutionSqlShapeTest} 钉住。</p>
     */
    public List<Map<String, Object>> findPoliciesForUser(String tableName, String userId) {
        String sql = """
            SELECT visible_cols, blocked_cols, priority, policy_name
            FROM ecos_cls_policy
            WHERE (table_name = ? OR resource_id = ?) AND enabled = true
              AND (user_id = ? OR user_id IS NULL)
              AND (role_id IS NULL OR role_id IN (
                  SELECT role_id FROM td_user_role WHERE user_id = ?
              ))
            ORDER BY priority ASC
            """;
        return jdbcTemplate.queryForList(sql, tableName, tableName, userId, userId);
    }

    /** CRUD 列表（可选按 tableName 过滤）。 */
    public List<Map<String, Object>> listPolicies(String tableName) {
        if (tableName != null && !tableName.isBlank()) {
            return jdbcTemplate.queryForList(
                    "SELECT * FROM " + TABLE + " WHERE table_name = ? ORDER BY priority ASC, created_at DESC",
                    tableName);
        }
        return jdbcTemplate.queryForList(
                "SELECT * FROM " + TABLE + " ORDER BY priority ASC, created_at DESC");
    }

    public List<Map<String, Object>> findByPolicyId(String id) {
        return jdbcTemplate.queryForList("SELECT * FROM " + TABLE + " WHERE id = ?", id);
    }

    public int createPolicy(String id, Object policyName, Object tableName, String visibleColsJson,
                            String blockedColsJson, Object roleId, Object userId,
                            Object priority, Object enabled, Object description, Object createdBy) {
        return jdbcTemplate.update(
            "INSERT INTO " + TABLE + " (id, policy_name, table_name, visible_cols, blocked_cols, role_id, user_id, priority, enabled, description, created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            id, policyName, tableName, visibleColsJson, blockedColsJson,
            roleId, userId, priority, enabled, description, createdBy);
    }

    public int updatePolicy(Object policyName, Object tableName, String visibleColsJson,
                            String blockedColsJson, Object roleId, Object userId,
                            Object priority, Object enabled, Object description, String id) {
        return jdbcTemplate.update(
            "UPDATE " + TABLE + " SET policy_name=?, table_name=?, visible_cols=?, blocked_cols=?, role_id=?, user_id=?, priority=?, enabled=?, description=?, updated_at=NOW() WHERE id=?",
            policyName, tableName, visibleColsJson, blockedColsJson,
            roleId, userId, priority, enabled, description, id);
    }

    public int deletePolicy(String id) {
        return jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id = ?", id);
    }
}
