package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.dto.SkySecurityPolicyUpsertDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.*;

/**
 * PMO-60 v2.0 T4a — 安全策略 CRUD 控制器。
 * <p>
 * 对应真表 {@code ecos_security_policy}（V148），替代原 sys_dict 字典表兜底。
 * 提供场景绑定的安全策略（ABAC 表达式 + 优先级路由）的增删改查能力。
 * <p>
 * 路径前缀 {@code /api/v1/security/policies}，未被 {@code VersionPrefixRewriteFilter} 重写（KEEP）。
 * 鉴权：{@code SecurityConfig} 中该前缀不在 permitAll，走 {@code .anyRequest().authenticated()}（需 Bearer Token）。
 * 准入：{@code ClearanceInterceptor} 对该前缀豁免检查（path.startsWith("/api/v1/security")），仅要求认证。
 * <p>
 * 写操作审计：通过 {@code EventBusService}（可选注入）发送事件到
 * {@code KafkaTopics.AUDIT}（{@code ecos.audit}）topic。不可用时仅 WARN 日志，不阻塞主流程。
 */
@RestController
@RequestMapping("/api/v1/security/policies")
public class SecurityPolicyController {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyController.class);
    private static final String TABLE = "ecos_security_policy";

    private final JdbcTemplate jdbc;
    private final ObjectProvider<Object> eventBusProvider;

    /**
     * 构造器注入。
     *
     * @param jdbc             Spring 自动配置的 JdbcTemplate（sysman-boot application.yml 已含 datasource 配置）
     * @param eventBusProvider 事件总线可选注入（runtime-event 的 EventBusService），不可用时为 empty
     */
    public SecurityPolicyController(JdbcTemplate jdbc,
                                    ObjectProvider<Object> eventBusProvider) {
        this.jdbc = jdbc;
        this.eventBusProvider = eventBusProvider;
    }

    // ── RowMapper ──────────────────────────────────────────────

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

    // ── GET / — 列表 ─────────────────────────────────────────────

    /**
     * 查询所有未删除的安全策略，按 priority 降序。
     *
     * @return {@code ApiResponse} 包装的策略列表
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        try {
            String sql = "SELECT id, name, domain, policy_expr, priority, "
                    + "create_time, update_time, create_by "
                    + "FROM " + TABLE + " WHERE is_deleted = 0 ORDER BY priority DESC";
            List<Map<String, Object>> result = jdbc.query(sql, ROW_MAPPER);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询安全策略列表失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    // ── GET /{id} — 详情 ─────────────────────────────────────────────

    /**
     * 按 ID 查询单条安全策略。
     *
     * @param id 策略主键（sp_xxxxxxxx）
     * @return {@code ApiResponse} 包装的策略详情；不存在返回 notFound
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(@PathVariable String id) {
        try {
            String sql = "SELECT id, name, domain, policy_expr, priority, "
                    + "create_time, update_time, create_by "
                    + "FROM " + TABLE + " WHERE id = ? AND is_deleted = 0";
            List<Map<String, Object>> rows = jdbc.query(sql, ROW_MAPPER, id);
            if (rows.isEmpty()) {
                return ApiResponse.notFound("安全策略不存在: " + id);
            }
            return ApiResponse.success(rows.get(0));
        } catch (Exception e) {
            log.error("查询安全策略详情失败: id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /** B12: 需 admin 角色（HeaderAuthInterceptor 的 X-ECOS-ROLE 判定）才可查完整 ABAC 表达式原文。 */
    @GetMapping("/{id}/expr")
    public ApiResponse<Map<String, Object>> getFullExpr(
            @PathVariable String id,
            @RequestHeader(value = "X-ECOS-ROLE", required = false) String role) {
        if (!"ADMIN".equalsIgnoreCase(role) && !"SECURITY_AUDITOR".equalsIgnoreCase(role)) {
            return ApiResponse.forbidden("需要 admin 或 security_auditor 角色才能查看策略表达式原文");
        }
        try {
            String sql = "SELECT id, name, domain, policy_expr, priority, "
                    + "create_time, update_time, create_by "
                    + "FROM " + TABLE + " WHERE id = ? AND is_deleted = 0";
            List<Map<String, Object>> rows = jdbc.query(sql, ROW_MAPPER_FULL, id);
            if (rows.isEmpty()) {
                return ApiResponse.notFound("安全策略不存在: " + id);
            }
            return ApiResponse.success(rows.get(0));
        } catch (Exception e) {
            log.error("查询策略表达式失败: id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    // ── POST / — 创建 ─────────────────────────────────────────────

    /**
     * 创建安全策略。自动生成 ID（sp_ + 8位UUID片段）。
     * 创建成功后发送审计事件到 ecos.audit topic。
     *
     * @param dto 请求体 DTO：name（必填）、domain、policyExpr（必填）、priority
     * @return {@code ApiResponse} 包装的新建策略
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody SkySecurityPolicyUpsertDTO dto) {
        String name = dto.getName();
        String policyExpr = dto.getPolicyExpr();
        if (name == null || name.isBlank()) {
            return ApiResponse.badRequest("name 不能为空");
        }
        if (policyExpr == null || policyExpr.isBlank()) {
            return ApiResponse.badRequest("policyExpr 不能为空");
        }
        String domain = dto.getDomain();
        int priority = dto.getPriority() != null ? dto.getPriority() : 100;

        try {
            String id = "sp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String sql = "INSERT INTO " + TABLE
                    + " (id, name, domain, policy_expr, priority, create_by, update_by, is_deleted, create_time, update_time)"
                    + " VALUES (?, ?, ?, ?, ?, 'system', 'system', 0, NOW(), NOW())";
            jdbc.update(sql, id, name, domain, policyExpr, priority);
            log.info("安全策略创建成功: id={}, name={}", id, name);

            // 审计事件 — 通过 EventBusService 发送到 Kafka（可选，不可用不阻塞）
            publishAuditEvent("security_policy_create", id, name);

            return ApiResponse.success(Map.of("id", id, "name", name));
        } catch (Exception e) {
            log.error("创建安全策略失败: name={}", name, e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    // ── PUT /{id} — 更新 ─────────────────────────────────────────────

    /**
     * 更新安全策略（name/domain/policy_expr/priority）。不允许跨改 id。
     * 更新成功后发送审计事件。
     *
     * @param id  策略主键
     * @param dto 请求体 DTO：name、domain、policyExpr、priority
     * @return {@code ApiResponse} 包装的更新结果
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id,
                                                   @RequestBody SkySecurityPolicyUpsertDTO dto) {
        // 校验记录存在
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM " + TABLE + " WHERE id = ? AND is_deleted = 0",
                    Integer.class, id);
            if (count == null || count == 0) {
                return ApiResponse.notFound("安全策略不存在: " + id);
            }
        } catch (Exception e) {
            log.error("校验安全策略存在性失败: id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }

        String name = dto.getName();
        String domain = dto.getDomain();
        String policyExpr = dto.getPolicyExpr();
        Integer priority = dto.getPriority();

        if (name != null && name.isBlank()) {
            return ApiResponse.badRequest("name 不能为空字符串");
        }
        if (policyExpr != null && policyExpr.isBlank()) {
            return ApiResponse.badRequest("policyExpr 不能为空字符串");
        }

        try {
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
            int rows = jdbc.update(sql, name, domain, policyExpr, priority, id);
            if (rows == 0) {
                return ApiResponse.notFound("安全策略不存在或已删除: " + id);
            }
            log.info("安全策略更新成功: id={}", id);

            publishAuditEvent("security_policy_update", id, name != null ? name : "updated");
            return ApiResponse.success(Map.of("id", id, "updated", true));
        } catch (Exception e) {
            log.error("更新安全策略失败: id={}", id, e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    // ── DELETE /{id} — 逻辑删除 ─────────────────────────────────────────────

    /**
     * 逻辑删除安全策略（is_deleted → 1）。删除成功后发送审计事件。
     *
     * @param id 策略主键
     * @return {@code ApiResponse} 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        try {
            int rows = jdbc.update(
                    "UPDATE " + TABLE + " SET is_deleted = 1, update_time = NOW() WHERE id = ? AND is_deleted = 0",
                    id);
            if (rows == 0) {
                return ApiResponse.notFound("安全策略不存在或已删除: " + id);
            }
            log.info("安全策略已逻辑删除: id={}", id);

            publishAuditEvent("security_policy_delete", id, "deleted");
            return ApiResponse.success(Map.of("id", id, "deleted", true));
        } catch (Exception e) {
            log.error("删除安全策略失败: id={}", id, e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    // ── 审计事件辅助 ─────────────────────────────────────────────

    /**
     * 发布审计事件到 ecos.audit topic。
     * <p>
     * 通过 ObjectProvider 可选注入 EventBusService（避免硬依赖 runtime-event / Kafka）。
     * 发送失败或不可用时仅 WARN 日志，不阻塞主流程（铁律 §2.4-5 审计不阻塞主流程）。
     *
     * @param eventType 事件类型（security_policy_create / update / delete）
     * @param id        策略 ID
     * @param name      策略名称
     */
    private void publishAuditEvent(String eventType, String id, String name) {
        try {
            Object eventBus = eventBusProvider.getIfAvailable();
            if (eventBus == null) {
                log.debug("[audit] EventBusService 不可用，跳过审计事件: {}", eventType);
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", eventType);
            payload.put("source", "sysman-security-policy");
            payload.put("aggregateType", "securityPolicy");
            payload.put("aggregateId", id);
            payload.put("name", name);
            payload.put("timestamp", System.currentTimeMillis());

            // 通过反射调用 publish 方法，避免编译期硬依赖 runtime-event
            eventBus.getClass().getMethod("publish", String.class, Object.class)
                    .invoke(eventBus, "ecos.audit", payload);
            log.debug("[audit] 审计事件已发布: {} id={}", eventType, id);
        } catch (Exception e) {
            log.warn("[audit] 审计事件发布失败（不阻塞主流程）: eventType={}, id={}, {}",
                    eventType, id, e.getMessage());
        }
    }
}
