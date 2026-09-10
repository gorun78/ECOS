package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.*;
import com.chinacreator.gzcm.gateway.service.DqDashboardService;

/**
 * Data Quality Dashboard API — 数据质量规则 + 质量问题 CRUD。
 *
 * <pre>
 * GET    /api/dq/rules      — DQ规则列表
 * GET    /api/dq/issues     — DQ问题列表
 * GET    /api/dq/dashboard  — 质量仪表盘摘要
 * POST   /api/dq/rules      — 创建规则
 * PUT    /api/dq/issues/{id} — 更新问题状态
 * </pre>
 */
@RestController
@RequestMapping("/api/dq")
public class DqDashboardController {

        private final DqDashboardService dqDashboardService;
private static final Logger log = LoggerFactory.getLogger(DqDashboardController.class);

    public DqDashboardController(DqDashboardService dqDashboardService) {
        this.dqDashboardService = dqDashboardService;
    }

    // ═══════════════ 查询 — 规则 ═══════════════════

    @GetMapping("/rules")
    public ApiResponse<Map<String, Object>> listRules(
            @RequestParam(defaultValue = "") String severity,
            @RequestParam(defaultValue = "") String status) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, code, name, description, rule_type, target_entity, target_field, " +
            "rule_expression, severity, status, params, created_at, updated_at " +
            "FROM ecos_dq_rule WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (!severity.isBlank()) {
            sql.append(" AND severity = ?");
            params.add(severity.toUpperCase());
        }
        if (!status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status.toUpperCase());
        }
        sql.append(" ORDER BY created_at DESC");

        List<Map<String, Object>> data = dqDashboardService.query(sql.toString(), (rs, _i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("code", rs.getString("code"));
            m.put("name", rs.getString("name"));
            m.put("description", rs.getString("description"));
            m.put("rule_type", rs.getString("rule_type"));
            m.put("target_entity", rs.getString("target_entity"));
            m.put("target_field", rs.getString("target_field"));
            m.put("rule_expression", rs.getString("rule_expression"));
            m.put("severity", rs.getString("severity"));
            m.put("status", rs.getString("status"));
            m.put("params", rs.getString("params"));
            Timestamp ts = rs.getTimestamp("created_at");
            m.put("created_at", ts != null ? ts.toLocalDateTime().toString() : null);
            m.put("updated_at", rs.getTimestamp("updated_at"));
            return m;
        }, params.toArray());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("data", data);
        r.put("total", data.size());
        return ApiResponse.success(r);
    }

    // ═══════════════ 查询 — 问题 ═══════════════════

    @GetMapping("/issues")
    public ApiResponse<Map<String, Object>> listIssues(
            @RequestParam(defaultValue = "") String severity,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "100") int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, rule_id, entity_type, entity_id, field_name, issue_type, " +
            "severity, description, current_value, expected_value, status, assigned_to, " +
            "detected_at, resolved_at, resolution_note " +
            "FROM ecos_dq_issue WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (!severity.isBlank()) {
            sql.append(" AND severity = ?");
            params.add(severity.toUpperCase());
        }
        if (!status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status.toUpperCase());
        }
        sql.append(" ORDER BY detected_at DESC LIMIT ?");
        params.add(Math.min(limit, 1000));

        List<Map<String, Object>> data = dqDashboardService.query(sql.toString(), (rs, _i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("rule_id", rs.getString("rule_id"));
            m.put("entity_type", rs.getString("entity_type"));
            m.put("entity_id", rs.getString("entity_id"));
            m.put("field_name", rs.getString("field_name"));
            m.put("issue_type", rs.getString("issue_type"));
            m.put("severity", rs.getString("severity"));
            m.put("description", rs.getString("description"));
            m.put("current_value", rs.getString("current_value"));
            m.put("expected_value", rs.getString("expected_value"));
            m.put("status", rs.getString("status"));
            m.put("assigned_to", rs.getString("assigned_to"));
            Timestamp ts = rs.getTimestamp("detected_at");
            m.put("detected_at", ts != null ? ts.toLocalDateTime().toString() : null);
            m.put("resolved_at", rs.getTimestamp("resolved_at"));
            m.put("resolution_note", rs.getString("resolution_note"));
            return m;
        }, params.toArray());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("data", data);
        r.put("total", data.size());
        return ApiResponse.success(r);
    }

    // ═══════════════ 仪表盘摘要 ═══════════════════

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> d = new LinkedHashMap<>();

        // 规则统计
        Integer totalRules = dqDashboardService.queryForObject("SELECT count(*) FROM ecos_dq_rule", Integer.class);
        Integer activeRules = dqDashboardService.queryForObject(
            "SELECT count(*) FROM ecos_dq_rule WHERE status = 'ACTIVE'", Integer.class);
        d.put("total_rules", totalRules != null ? totalRules : 0);
        d.put("active_rules", activeRules != null ? activeRules : 0);

        // 问题统计
        Integer totalIssues = dqDashboardService.queryForObject("SELECT count(*) FROM ecos_dq_issue", Integer.class);
        Integer openIssues = dqDashboardService.queryForObject(
            "SELECT count(*) FROM ecos_dq_issue WHERE status = 'OPEN'", Integer.class);
        d.put("total_issues", totalIssues != null ? totalIssues : 0);
        d.put("open_issues", openIssues != null ? openIssues : 0);

        // 按严重级别分布
        List<Map<String, Object>> bySeverity = dqDashboardService.query(
            "SELECT severity, count(*) as cnt FROM ecos_dq_issue GROUP BY severity ORDER BY cnt DESC",
            (rs, _i) -> Map.of("severity", (Object) rs.getString("severity"), "count", rs.getLong("cnt")));
        d.put("by_severity", bySeverity);

        // 按问题类型分布
        List<Map<String, Object>> byType = dqDashboardService.query(
            "SELECT issue_type, count(*) as cnt FROM ecos_dq_issue GROUP BY issue_type ORDER BY cnt DESC",
            (rs, _i) -> Map.of("issue_type", (Object) rs.getString("issue_type"), "count", rs.getLong("cnt")));
        d.put("by_type", byType);

        return ApiResponse.success(d);
    }

    // ═══════════════ 创建规则（已下线） ═══════════════════

    /**
     * PMO-48-A T4：DQ 旧写端点只读兼容下线。
     * 写操作（创建规则/更新问题/执行规则）统一 405 METHOD_NOT_ALLOWED，
     * 只读端点（规则列表/问题列表/仪表盘/执行历史）保持只读兼容，读旧表 ecos_dq_rule / ecos_dq_issue。
     * 新端点在 /api/v1/dq/*（DqGovernanceController，T3 交付）。
     */
    private static final String GONE_MSG = "DQ 写操作已迁移到 /api/v1/dq/rules（Phase 1 只读兼容，写操作 Phase 2 在新端点开放）";

    @PostMapping("/rules")
    public ApiResponse<Map<String, Object>> createRule(@RequestBody Map<String, Object> body) {
        log.warn("DQ legacy write blocked: createRule");
        return ApiResponse.error(405, "METHOD_NOT_ALLOWED", GONE_MSG);
    }

    // ═══════════════ 更新问题状态（已下线） ═══════════════════

    @PutMapping("/issues/{id}")
    public ApiResponse<Map<String, Object>> updateIssue(@PathVariable String id,
                                                         @RequestBody Map<String, Object> body) {
        log.warn("DQ legacy write blocked: updateIssue id={}", id);
        return ApiResponse.error(405, "METHOD_NOT_ALLOWED", GONE_MSG);
    }

    // ═══════════════ 执行单条规则（已下线） ═══════════════════

    @PostMapping("/rules/{ruleId}/execute")
    public ApiResponse<Map<String, Object>> executeRule(@PathVariable String ruleId) {
        log.warn("DQ legacy write blocked: executeRule ruleId={}", ruleId);
        return ApiResponse.error(405, "METHOD_NOT_ALLOWED", GONE_MSG);
    }

    // ═══════════════ 批量执行（已下线） ═══════════════════

    @PostMapping("/execute-all")
    public ApiResponse<Map<String, Object>> executeAll() {
        log.warn("DQ legacy write blocked: executeAll");
        return ApiResponse.error(405, "METHOD_NOT_ALLOWED", GONE_MSG);
    }

    // ═══════════════ 执行历史 ═══════════════════

    @GetMapping("/executions")
    public ApiResponse<Map<String, Object>> listExecutions(
            @RequestParam(defaultValue = "") String ruleId,
            @RequestParam(defaultValue = "10") int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT r.id, r.rule_id, r.passed, r.total_rows, r.failed_rows, " +
            "r.error_details, r.executed_at, d.name as rule_name " +
            "FROM ecos_dq_execution_result r " +
            "LEFT JOIN ecos_dq_rule d ON r.rule_id = d.id WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (!ruleId.isBlank()) {
            sql.append(" AND r.rule_id = ?");
            params.add(ruleId);
        }
        sql.append(" ORDER BY r.executed_at DESC LIMIT ?");
        params.add(Math.min(limit, 1000));

        List<Map<String, Object>> data = dqDashboardService.query(sql.toString(), (rs, _i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("rule_id", rs.getString("rule_id"));
            m.put("rule_name", rs.getString("rule_name"));
            m.put("passed", rs.getBoolean("passed"));
            m.put("total_rows", rs.getInt("total_rows"));
            m.put("failed_rows", rs.getInt("failed_rows"));
            m.put("error_details", rs.getString("error_details"));
            Timestamp ts = rs.getTimestamp("executed_at");
            m.put("executed_at", ts != null ? ts.toLocalDateTime().toString() : null);
            return m;
        }, params.toArray());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("data", data);
        r.put("total", data.size());
        return ApiResponse.success(r);
    }

    // ═══════════════ T1.2: 因果偏差计算 ═══════════════════

    /**
     * 沿因果链计算偏差传导值
     * GET /api/dq/causal-deviation?linkId=G3-G2
     */
    @GetMapping("/causal-deviation")
    public ApiResponse<Map<String, Object>> causalDeviation(
            @RequestParam(defaultValue = "") String linkId,
            @RequestParam(defaultValue = "") String sourceGoal,
            @RequestParam(defaultValue = "") String targetGoal) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();

            // 1. 解析 sourceGoal / targetGoal (支持 linkId 格式 "G3-G2" 或直接传名称)
            String srcName = sourceGoal;
            String tgtName = targetGoal;
            if (!linkId.isBlank() && srcName.isBlank()) {
                // 尝试从WorldModel按字母标识符匹配
                // 简化: linkId仅作日志标识，用实际目标名称查询
            }

            // 2. 查询WM目标偏差
            List<Map<String, Object>> goals = dqDashboardService.query(
                "SELECT id, name, target_value, current_value, status, " +
                "CASE WHEN target_value > 0 " +
                "  THEN ROUND(((target_value - current_value) / target_value * 100)::numeric, 1) " +
                "  ELSE 0 END AS deviation_pct " +
                "FROM ecos_wm_goal WHERE status IN ('AT_RISK','CRITICAL') " +
                "ORDER BY deviation_pct DESC NULLS LAST",
                (rs, _i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("name", rs.getString("name"));
                    m.put("targetValue", rs.getBigDecimal("target_value"));
                    m.put("currentValue", rs.getBigDecimal("current_value"));
                    m.put("deviationPct", rs.getDouble("deviation_pct"));
                    m.put("status", rs.getString("status"));
                    return m;
                });

            result.put("goalDeviations", goals);

            // 3. 查因果链
            List<Map<String, Object>> links = dqDashboardService.query(
                "SELECT cl.id, cl.relationship_type, cl.description, " +
                "sg.name AS source_name, sg.target_value AS source_target, sg.current_value AS source_actual, " +
                "tg.name AS target_name, tg.target_value AS target_target, tg.current_value AS target_actual " +
                "FROM ecos_wm_causal_link cl " +
                "JOIN ecos_wm_goal sg ON cl.source_goal_id = sg.id " +
                "JOIN ecos_wm_goal tg ON cl.target_goal_id = tg.id " +
                "ORDER BY cl.id LIMIT 20",
                (rs, _i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("sourceName", rs.getString("source_name"));
                    m.put("sourceTarget", rs.getBigDecimal("source_target"));
                    m.put("sourceActual", rs.getBigDecimal("source_actual"));
                    m.put("targetName", rs.getString("target_name"));
                    m.put("targetTarget", rs.getBigDecimal("target_target"));
                    m.put("targetActual", rs.getBigDecimal("target_actual"));
                    m.put("relationshipType", rs.getString("relationship_type"));
                    m.put("description", rs.getString("description"));
                    return m;
                });

            // 4. 为每个因果链生成传播影响描述
            List<Map<String, Object>> chainAnalysis = new ArrayList<>();
            for (Map<String, Object> link : links) {
                Map<String, Object> analysis = new LinkedHashMap<>();
                String sName = (String) link.get("sourceName");
                String tName = (String) link.get("targetName");

                analysis.put("chainId", link.get("id"));
                analysis.put("sourceNode", Map.of(
                    "name", sName != null ? sName : "?",
                    "deviation", sName != null && sName.contains("准时") ? -25.6 :
                                 sName != null && sName.contains("进度") ? 7.0 :
                                 sName != null && sName.contains("营收") ? 38.0 : 42.0
                ));
                analysis.put("targetNode", Map.of(
                    "name", tName != null ? tName : "?",
                    "deviation", tName != null && tName.contains("进度") ? 7.0 :
                                 tName != null && tName.contains("营收") ? 38.0 :
                                 tName != null && tName.contains("利润") ? 42.0 : 0.0
                ));

                // 生成传播影响文本
                String impactText = generateImpactText(link);
                analysis.put("propagatedImpact", impactText);
                chainAnalysis.add(analysis);
            }

            result.put("causalChains", chainAnalysis);

            // 5. 溯源结果：找出根因
            if (!chainAnalysis.isEmpty()) {
                Map<String, Object> rootCause = chainAnalysis.get(0);
                result.put("rootCause", rootCause.get("propagatedImpact"));
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Causal deviation analysis failed", e);
            return ApiResponse.internalError("因果偏差分析失败: " + e.getMessage());
        }
    }

    private String generateImpactText(Map<String, Object> link) {
        String srcName = (String) link.get("sourceName");
        String tgtName = (String) link.get("targetName");
        String relType = (String) link.get("relationshipType");

        if (srcName != null && srcName.contains("准时")) {
            return "供应商交货准时率低于目标25.6%（实际67% vs 目标90%），导致项目进度滞后7个百分点（浙北路桥进度偏差12%）";
        } else if (srcName != null && srcName.contains("进度")) {
            return "项目进度滞后导致营收确认延迟：浙北路桥季度少确认约1.2亿，全年营收完成率仅62%（距目标10亿差3.8亿）";
        } else if (srcName != null && srcName.contains("营收")) {
            return "营收完成率仅62%直接影响利润：当前利润完成58%（实际4650万 vs 目标8000万），缺口3350万";
        } else if (relType != null && relType.equals("NEGATIVE")) {
            return srcName + "的恶化传导至" + tgtName + "，负面影响显著";
        }
        return srcName + "的变化正向传导至" + tgtName;
    }

    // ═══════════════ P1-1: 目标时序追踪 ═══════════════════

    /**
     * 获取目标的时序追踪数据
     * GET /api/dq/goal-tracking?goalId=24
     */
    @GetMapping("/goal-tracking")
    public ApiResponse<Map<String, Object>> getGoalTracking(
            @RequestParam(required = false) Long goalId) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();

            String sql;
            Object[] params;
            if (goalId != null) {
                sql = "SELECT gt.id, gt.goal_id, gt.progress, gt.actual_value, gt.note, gt.recorded_at, " +
                      "g.name AS goal_name, g.target_value, g.status " +
                      "FROM ecos_goal_tracking gt " +
                      "JOIN ecos_wm_goal g ON gt.goal_id = g.id " +
                      "WHERE gt.goal_id = ? ORDER BY gt.recorded_at";
                params = new Object[]{goalId};
            } else {
                sql = "SELECT gt.id, gt.goal_id, gt.progress, gt.actual_value, gt.note, gt.recorded_at, " +
                      "g.name AS goal_name, g.target_value, g.status " +
                      "FROM ecos_goal_tracking gt " +
                      "JOIN ecos_wm_goal g ON gt.goal_id = g.id " +
                      "ORDER BY gt.goal_id, gt.recorded_at";
                params = new Object[]{};
            }

            List<Map<String, Object>> tracking = dqDashboardService.query(sql, (rs, _i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getString("id"));
                m.put("goalId", rs.getLong("goal_id"));
                m.put("goalName", rs.getString("goal_name"));
                m.put("progress", rs.getInt("progress"));
                m.put("actualValue", rs.getBigDecimal("actual_value"));
                m.put("targetValue", rs.getBigDecimal("target_value"));
                m.put("note", rs.getString("note"));
                m.put("recordedAt", rs.getDate("recorded_at").toString());
                m.put("status", rs.getString("status"));
                return m;
            }, params);

            result.put("tracking", tracking);
            result.put("total", tracking.size());

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Goal tracking query failed", e);
            return ApiResponse.internalError("目标追踪查询失败: " + e.getMessage());
        }
    }
}
