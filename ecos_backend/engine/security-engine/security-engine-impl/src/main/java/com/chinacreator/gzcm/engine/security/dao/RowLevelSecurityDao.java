package com.chinacreator.gzcm.engine.security.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 行级安全（RLS）策略 DAO（R2-B3b 数据访问收口；只搬不改语义）。
 *
 * <p>表：{@code ecos_rls_policy}（+ 角色子查询 {@code TD_USER_ROLE}）。
 * 供 {@code RowLevelSecurityServiceImpl} 使用；RlsPredicateValidator 校验与
 * 谓词合并逻辑留在 service。</p>
 */
@Repository
public class RowLevelSecurityDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_rls_policy";

    /**
     * 命中策略查询（table_name OR resource_id 双轨 + user/role 匹配，priority 升序，含 V164 参数化列）。
     *
     * <p><b>#142/B4-3a 缺陷修复（详设 §4.7.2 角色×通道矩阵的角色维度在真实库上从未生效）</b>：
     * 角色子查询原写作 {@code SELECT "ROLE_ID" FROM TD_USER_ROLE WHERE "USER_ID" = ?}。
     * PostgreSQL 双引号标识符<b>大小写敏感</b>，而 {@code td_user_role} 的全仓 DDL 单源
     * （{@code docs/50-db_script/01-ddl-多方言/postgresql/01_ecos_sysman.sql:122}、
     * {@code 06-docker-infra/iam_pg_seed.sql:57}）建的列名是<b>不加引号的小写</b>
     * {@code user_id/role_id}（PG 未加引号即折叠小写），因此该语句在 dev PG 上必然报错——
     * 本批实跑凭证（改前，只读）：{@code ERROR: column "ROLE_ID" does not exist}；
     * 改后同形语句在按 DDL 单源建的 {@code TEMP} 表上按角色各命中 1 条策略（详见差距文档 v1.0.14）。</p>
     *
     * <p>异常在 {@code RowLevelSecurityServiceImpl.apply} 里被 catch 成"无策略"，
     * 于是 {@code predicateTemplate=1=1 + denyIfEmpty=true}——即<b>角色行级过滤整体失效</b>
     * 而对外仍像"策略在位"。这正是差距文档 D-15/D-16 记录的"mock 绿、真库断"形态，
     * 具名门禁 {@code RoleResolutionSqlShapeTest} 已把该 SQL 形态钉住（禁再写引号大写）。</p>
     *
     * <p>写法取<b>不加引号</b>形态：PG 折叠小写命中现网列；Oracle 折叠大写命中其未加引号建的同名列；
     * MySQL 列名大小写不敏感——三档方言唯一同时成立的写法（多库兼容口径见数据库访问规范 §多库兼容）。</p>
     */
    public List<Map<String, Object>> findPoliciesForUser(String tableName, String userId) {
        String sql = """
            SELECT filter_expr, priority, policy_name,
                   predicate_template, bindings_json, source_kind
            FROM ecos_rls_policy
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

    public int createPolicy(String id, Object policyName, Object tableName, Object filterExpr,
                            Object roleId, Object userId, Object priority, Object enabled,
                            Object description, Object createdBy) {
        return jdbcTemplate.update(
            "INSERT INTO " + TABLE + " (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled, description, created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
            id, policyName, tableName, filterExpr,
            roleId, userId, priority, enabled, description, createdBy);
    }

    public int updatePolicy(Object policyName, Object tableName, Object filterExpr,
                            Object roleId, Object userId, Object priority, Object enabled,
                            Object description, String id) {
        return jdbcTemplate.update(
            "UPDATE " + TABLE + " SET policy_name=?, table_name=?, filter_expr=?, role_id=?, user_id=?, priority=?, enabled=?, description=?, updated_at=NOW() WHERE id=?",
            policyName, tableName, filterExpr,
            roleId, userId, priority, enabled, description, id);
    }

    public int deletePolicy(String id) {
        return jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id = ?", id);
    }

    /** upsert 存在性探针。 */
    public Object existsProbe(String id) {
        return jdbcTemplate.query("SELECT id FROM " + TABLE + " WHERE id = ?",
                r -> r.next() ? r.getObject(1) : null, id);
    }

    /** 参数化 upsert — INSERT 分支（V164 新列 predicate_template / bindings_json / source_kind='parameterized'）。 */
    public int insertParameterized(String id, String policyName, String tableName, String template,
                                   Object roleId, Object userId, int priority, int enabledInt,
                                   String bindingsJson, Object createdBy) {
        return jdbcTemplate.update(
            "INSERT INTO " + TABLE + " (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled, "
                    + "predicate_template, bindings_json, source_kind, version_no, is_deleted, created_at, updated_at, created_by) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,'parameterized','1',0,NOW(),NOW(),?)",
            id, policyName, tableName, template,
            roleId, userId, priority, enabledInt,
            template, bindingsJson, createdBy);
    }

    /** 参数化 upsert — UPDATE 分支。 */
    public int updateParameterized(String policyName, String tableName, String template,
                                   Object roleId, Object userId, int priority, int enabledInt,
                                   String bindingsJson, String id) {
        return jdbcTemplate.update(
            "UPDATE " + TABLE + " SET policy_name=?, table_name=?, filter_expr=?, role_id=?, user_id=?, priority=?, enabled=?, "
                    + "predicate_template=?, bindings_json=?, source_kind='parameterized', updated_at=NOW() WHERE id=?",
            policyName, tableName, template,
            roleId, userId, priority, enabledInt,
            template, bindingsJson, id);
    }
}
