package com.chinacreator.gzcm.sysman.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SecurityPolicyQueryService — 安全策略（{@code ecos_security_policy}，V148）数据访问层。
 * <p>
 * PMO-74 H9-T4：从 {@code SecurityPolicyController} 下沉的 JdbcTemplate 访问层
 * （硬规则「Controller 禁止直用 JdbcTemplate — 必过 Service」，铁律 §1.5 /
 * 《后端开发规范》分层约束）。SQL、结果集列→Map 键映射与 ID 生成全部收敛到本类，
 * Controller 仅做入参校验、{@code ApiResponse} 包装与审计事件发布。
 * <p>
 * 语义与下沉前逐字段等价：
 * <ul>
 *   <li>{@link #listAll()} / {@link #findSummaryById(String)} 走摘要 RowMapper
 *       （B12：{@code policyExpr} 截断为前 50 字符的 {@code policyExprSummary}，
 *       不外泄 ABAC 策略原文）；</li>
 *   <li>{@link #findFullExprById(String)} 走完整 RowMapper（额外含 {@code policyExpr} 原文），
 *       仅由 admin / security_auditor 角色端点调用；</li>
 *   <li>更新走 {@code COALESCE} 局部更新（null 不覆盖既有值），删除为逻辑删除
 *       （{@code is_deleted = 1}，铁律 4.8）。</li>
 * </ul>
 * 数据源异常原样抛出，由 Controller 的 try-catch 归一为 {@code ApiResponse.internalError}。
 */
@Service
public class SecurityPolicyQueryService {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyQueryService.class);

    private static final String TABLE = "ecos_security_policy";

    private static final String COLUMNS = "id, name, domain, policy_expr, priority, "
            + "create_time, update_time, create_by";

    private final JdbcTemplate jdbc;

    public SecurityPolicyQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── RowMapper ──────────────────────────────────────────────

    /** 列表/详情默认行映射：{@code policy_expr} 只出摘要（B12 防原文泄露）。 */
    private final RowMapper<Map<String, Object>> ROW_MAPPER = (rs, rowNum) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("name", rs.getString("name"));
        row.put("domain", rs.getString("domain"));
        String expr = rs.getString("policy_expr");
        // B12: 列表/详情默认返摘要（前50字符），防 ABAC 策略原文泄露
        row.put("policyExprSummary", expr != null && expr.length() > 50 ? expr.substring(0, 50) + "..." : expr);
        row.put("priority", rs.getInt("priority"));
        Timestamp ct = rs.getTimestamp("create_time");
        row.put("createTime", ct != null ? ct.getTime() : null);
        Timestamp ut = rs.getTimestamp("update_time");
        row.put("updateTime", ut != null ? ut.getTime() : null);
        row.put("createBy", rs.getString("create_by"));
        return row;
    };

    /** 详情端点专用：含完整 policy_expr（受 security-engine ABAC 评估保护）。 */
    private final RowMapper<Map<String, Object>> ROW_MAPPER_FULL = (rs, rowNum) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("name", rs.getString("name"));
        row.put("domain", rs.getString("domain"));
        String expr = rs.getString("policy_expr");
        row.put("policyExpr", expr);
        row.put("policyExprSummary", expr != null && expr.length() > 50 ? expr.substring(0, 50) + "..." : expr);
        row.put("priority", rs.getInt("priority"));
        Timestamp ct = rs.getTimestamp("create_time");
        row.put("createTime", ct != null ? ct.getTime() : null);
        Timestamp ut = rs.getTimestamp("update_time");
        row.put("updateTime", ut != null ? ut.getTime() : null);
        row.put("createBy", rs.getString("create_by"));
        return row;
    };

    // ── 查询 ───────────────────────────────────────────────────

    /**
     * 全部未删除策略（priority 降序，摘要形态）。
     *
     * @return 策略 Map 列表（永不为 null）
     */
    public List<Map<String, Object>> listAll() {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE is_deleted = 0 ORDER BY priority DESC";
        return jdbc.query(sql, ROW_MAPPER);
    }

    /**
     * 按 ID 查单条（摘要形态）。
     *
     * @param id 策略主键
     * @return 命中行；不存在返回 {@code null}
     */
    public Map<String, Object> findSummaryById(String id) {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE id = ? AND is_deleted = 0";
        List<Map<String, Object>> rows = jdbc.query(sql, ROW_MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 按 ID 查单条（含 {@code policyExpr} 原文，admin / security_auditor 专用）。
     *
     * @param id 策略主键
     * @return 命中行；不存在返回 {@code null}
     */
    public Map<String, Object> findFullExprById(String id) {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE id = ? AND is_deleted = 0";
        List<Map<String, Object>> rows = jdbc.query(sql, ROW_MAPPER_FULL, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 存在性校验计数（未删除）。
     *
     * @param id 策略主键
     * @return COUNT(1) 结果（理论上非 null，保留 {@code Integer} 以对齐下沉前的 null 判定）
     */
    public Integer countById(String id) {
        return jdbc.queryForObject(
                "SELECT COUNT(1) FROM " + TABLE + " WHERE id = ? AND is_deleted = 0",
                Integer.class, id);
    }

    // ── 写入 ───────────────────────────────────────────────────

    /**
     * 新建策略：生成 {@code sp_ + 8 位 UUID 片段} 主键并落库。
     *
     * @param name       策略名（调用方已校验非空）
     * @param domain     业务域（可空）
     * @param policyExpr ABAC 表达式（调用方已校验非空）
     * @param priority   优先级（调用方已补默认值）
     * @return 新生成的策略 ID
     */
    public String create(String name, String domain, String policyExpr, int priority) {
        String id = "sp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String sql = "INSERT INTO " + TABLE
                + " (id, name, domain, policy_expr, priority, create_by, update_by, is_deleted, create_time, update_time)"
                + " VALUES (?, ?, ?, ?, ?, 'system', 'system', 0, NOW(), NOW())";
        jdbc.update(sql, id, name, domain, policyExpr, priority);
        log.debug("ecos_security_policy insert ok: id={}, name={}", id, name);
        return id;
    }

    /**
     * 局部更新（{@code COALESCE}，null 不覆盖既有值）。
     *
     * @return 受影响行数（0 表示不存在或已删除）
     */
    public int update(String id, String name, String domain, String policyExpr, Integer priority) {
        String sql = """
                UPDATE %s SET
                    name = COALESCE(?, name),
                    domain = COALESCE(?, domain),
                    policy_expr = COALESCE(?, policy_expr),
                    priority = COALESCE(?, priority),
                    update_time = NOW(),
                    update_by = 'system'
                WHERE id = ? AND is_deleted = 0
                """.formatted(TABLE);
        return jdbc.update(sql, name, domain, policyExpr, priority, id);
    }

    /**
     * 逻辑删除（{@code is_deleted = 1}）。
     *
     * @return 受影响行数（0 表示不存在或已删除）
     */
    public int logicalDelete(String id) {
        return jdbc.update(
                "UPDATE " + TABLE + " SET is_deleted = 1, update_time = NOW() WHERE id = ? AND is_deleted = 0",
                id);
    }
}
