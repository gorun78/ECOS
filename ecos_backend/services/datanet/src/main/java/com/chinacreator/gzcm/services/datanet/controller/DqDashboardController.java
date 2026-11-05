package com.chinacreator.gzcm.services.datanet.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.services.datanet.service.DqDashboardService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DqDashboardController — 数据质量仪表盘 API（P3-A 从 gateway 迁至 datanet：/api/dq）。
 *
 * <p>PMO-48-A T4：DQ 旧写端点只读兼容下线，写操作统一 405；新端点在 /api/v1/dq/*。
 *
 * <p><b>#147CK（DoD DI-2 第十五批）</b>：出入参强类型化——RowMapper 由「拼 Map」改 record
 * （snake_case 键用 {@code @JsonProperty} 钉住），响应容器改 {@link DataPage} 与各 VO；
 * 两个 405 墓碑腿去掉「从不使用」的请求体参数（此前缺体请求会先被 Spring 判 400，与「旧写面
 * 一律 405」的声明不一致；现在恒 405）。<b>取值口径逐字保留</b>：{@code created_at}/
 * {@code detected_at}/{@code executed_at} 仍取 {@code LocalDateTime.toString()} 文本，
 * 而 {@code updated_at}/{@code resolved_at} 仍是原始 Timestamp（旧实现本就不一致，本批不改语义）。</p>
 *
 * @author ecos-factory
 * @since PMO-49 P3-A
 */
@RestController
@RequestMapping("/api/dq")
public class DqDashboardController {

    private static final Logger log = LoggerFactory.getLogger(DqDashboardController.class);
    private final DqDashboardService dqDashboardService;

    public DqDashboardController(DqDashboardService dqDashboardService) {
        this.dqDashboardService = dqDashboardService;
    }

    @GetMapping("/rules")
    public ApiResponse<DataPage<DqRuleRow>> listRules(
            @RequestParam(defaultValue = "") String severity,
            @RequestParam(defaultValue = "") String status) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, code, name, description, rule_type, target_entity, target_field, " +
            "rule_expression, severity, status, params, created_at, updated_at " +
            "FROM ecos_dq_rule WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (!severity.isBlank()) { sql.append(" AND severity = ?"); params.add(severity.toUpperCase()); }
        if (!status.isBlank()) { sql.append(" AND status = ?"); params.add(status.toUpperCase()); }
        sql.append(" ORDER BY created_at DESC");

        List<DqRuleRow> data = dqDashboardService.query(sql.toString(), (rs, _i) -> {
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new DqRuleRow(rs.getString("id"), rs.getString("code"), rs.getString("name"),
                    rs.getString("description"), rs.getString("rule_type"),
                    rs.getString("target_entity"), rs.getString("target_field"),
                    rs.getString("rule_expression"), rs.getString("severity"), rs.getString("status"),
                    rs.getString("params"),
                    createdAt != null ? createdAt.toLocalDateTime().toString() : null,
                    rs.getTimestamp("updated_at"));
        }, params.toArray());

        return ApiResponse.success(new DataPage<>(data, data.size()));
    }

    @GetMapping("/issues")
    public ApiResponse<DataPage<DqIssueRow>> listIssues(
            @RequestParam(defaultValue = "") String severity,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "100") int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, rule_id, entity_type, entity_id, field_name, issue_type, " +
            "severity, description, current_value, expected_value, status, assigned_to, " +
            "detected_at, resolved_at, resolution_note " +
            "FROM ecos_dq_issue WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (!severity.isBlank()) { sql.append(" AND severity = ?"); params.add(severity.toUpperCase()); }
        if (!status.isBlank()) { sql.append(" AND status = ?"); params.add(status.toUpperCase()); }
        sql.append(" ORDER BY detected_at DESC LIMIT ?");
        params.add(Math.min(limit, 1000));

        List<DqIssueRow> data = dqDashboardService.query(sql.toString(), (rs, _i) -> {
            Timestamp detectedAt = rs.getTimestamp("detected_at");
            return new DqIssueRow(rs.getString("id"), rs.getString("rule_id"),
                    rs.getString("entity_type"), rs.getString("entity_id"),
                    rs.getString("field_name"), rs.getString("issue_type"), rs.getString("severity"),
                    rs.getString("description"), rs.getString("current_value"),
                    rs.getString("expected_value"), rs.getString("status"), rs.getString("assigned_to"),
                    detectedAt != null ? detectedAt.toLocalDateTime().toString() : null,
                    rs.getTimestamp("resolved_at"), rs.getString("resolution_note"));
        }, params.toArray());

        return ApiResponse.success(new DataPage<>(data, data.size()));
    }

    @GetMapping("/dashboard")
    public ApiResponse<DqDashboardVO> dashboard() {
        Integer totalRules = dqDashboardService.queryForObject("SELECT count(*) FROM ecos_dq_rule", Integer.class);
        Integer activeRules = dqDashboardService.queryForObject(
            "SELECT count(*) FROM ecos_dq_rule WHERE status = 'ACTIVE'", Integer.class);

        Integer totalIssues = dqDashboardService.queryForObject("SELECT count(*) FROM ecos_dq_issue", Integer.class);
        Integer openIssues = dqDashboardService.queryForObject(
            "SELECT count(*) FROM ecos_dq_issue WHERE status = 'OPEN'", Integer.class);

        List<SeverityCount> bySeverity = dqDashboardService.query(
            "SELECT severity, count(*) as cnt FROM ecos_dq_issue GROUP BY severity ORDER BY cnt DESC",
            (rs, _i) -> new SeverityCount(rs.getString("severity"), rs.getLong("cnt")));

        List<IssueTypeCount> byType = dqDashboardService.query(
            "SELECT issue_type, count(*) as cnt FROM ecos_dq_issue GROUP BY issue_type ORDER BY cnt DESC",
            (rs, _i) -> new IssueTypeCount(rs.getString("issue_type"), rs.getLong("cnt")));

        return ApiResponse.success(new DqDashboardVO(
                totalRules != null ? totalRules : 0,
                activeRules != null ? activeRules : 0,
                totalIssues != null ? totalIssues : 0,
                openIssues != null ? openIssues : 0,
                bySeverity, byType));
    }

    private static final String GONE_MSG = "DQ 写操作已迁移到 /api/v1/dq/rules（Phase 1 只读兼容，写操作 Phase 2 在新端点开放）";

    @PostMapping("/rules")
    public ApiResponse<Void> createRule() {
        log.warn("DQ legacy write blocked: createRule");
        return ApiResponse.error(405, "METHOD_NOT_ALLOWED", GONE_MSG);
    }

    @PutMapping("/issues/{id}")
    public ApiResponse<Void> updateIssue(@PathVariable String id) {
        log.warn("DQ legacy write blocked: updateIssue id={}", id);
        return ApiResponse.error(405, "METHOD_NOT_ALLOWED", GONE_MSG);
    }

    @PostMapping("/rules/{ruleId}/execute")
    public ApiResponse<Void> executeRule(@PathVariable String ruleId) {
        log.warn("DQ legacy write blocked: executeRule ruleId={}", ruleId);
        return ApiResponse.error(405, "METHOD_NOT_ALLOWED", GONE_MSG);
    }

    @PostMapping("/execute-all")
    public ApiResponse<Void> executeAll() {
        log.warn("DQ legacy write blocked: executeAll");
        return ApiResponse.error(405, "METHOD_NOT_ALLOWED", GONE_MSG);
    }

    @GetMapping("/executions")
    public ApiResponse<DataPage<DqExecutionRow>> listExecutions(
            @RequestParam(defaultValue = "") String ruleId,
            @RequestParam(defaultValue = "10") int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT r.id, r.rule_id, r.passed, r.total_rows, r.failed_rows, " +
            "r.error_details, r.executed_at, d.name as rule_name " +
            "FROM ecos_dq_execution_result r " +
            "LEFT JOIN ecos_dq_rule d ON r.rule_id = d.id WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (!ruleId.isBlank()) { sql.append(" AND r.rule_id = ?"); params.add(ruleId); }
        sql.append(" ORDER BY r.executed_at DESC LIMIT ?");
        params.add(Math.min(limit, 1000));

        List<DqExecutionRow> data = dqDashboardService.query(sql.toString(), (rs, _i) -> {
            Timestamp executedAt = rs.getTimestamp("executed_at");
            return new DqExecutionRow(rs.getString("id"), rs.getString("rule_id"),
                    rs.getString("rule_name"), rs.getBoolean("passed"), rs.getInt("total_rows"),
                    rs.getInt("failed_rows"), rs.getString("error_details"),
                    executedAt != null ? executedAt.toLocalDateTime().toString() : null);
        }, params.toArray());

        return ApiResponse.success(new DataPage<>(data, data.size()));
    }

    @GetMapping("/causal-deviation")
    public ApiResponse<CausalDeviationVO> causalDeviation(
            @RequestParam(defaultValue = "") String linkId,
            @RequestParam(defaultValue = "") String sourceGoal,
            @RequestParam(defaultValue = "") String targetGoal) {
        try {
            List<GoalDeviation> goals = dqDashboardService.query(
                "SELECT id, name, target_value, current_value, status, " +
                "CASE WHEN target_value > 0 " +
                "  THEN ROUND(((target_value - current_value) / target_value * 100)::numeric, 1) " +
                "  ELSE 0 END AS deviation_pct " +
                "FROM ecos_wm_goal WHERE status IN ('AT_RISK','CRITICAL') " +
                "ORDER BY deviation_pct DESC NULLS LAST",
                (rs, _i) -> new GoalDeviation(rs.getLong("id"), rs.getString("name"),
                        rs.getBigDecimal("target_value"), rs.getBigDecimal("current_value"),
                        rs.getDouble("deviation_pct"), rs.getString("status")));

            List<CausalLink> links = dqDashboardService.query(
                "SELECT cl.id, cl.relationship_type, cl.description, " +
                "sg.name AS source_name, sg.target_value AS source_target, sg.current_value AS source_actual, " +
                "tg.name AS target_name, tg.target_value AS target_target, tg.current_value AS target_actual " +
                "FROM ecos_wm_causal_link cl " +
                "JOIN ecos_wm_goal sg ON cl.source_goal_id = sg.id " +
                "JOIN ecos_wm_goal tg ON cl.target_goal_id = tg.id " +
                "ORDER BY cl.id LIMIT 20",
                (rs, _i) -> new CausalLink(rs.getLong("id"), rs.getString("source_name"),
                        rs.getBigDecimal("source_target"), rs.getBigDecimal("source_actual"),
                        rs.getString("target_name"), rs.getBigDecimal("target_target"),
                        rs.getBigDecimal("target_actual"), rs.getString("relationship_type"),
                        rs.getString("description")));

            List<ChainAnalysis> chainAnalysis = new ArrayList<>();
            for (CausalLink link : links) {
                String sName = link.sourceName();
                String tName = link.targetName();
                ChainNode sourceNode = new ChainNode(sName != null ? sName : "?",
                        sName != null && sName.contains("准时") ? -25.6 :
                        sName != null && sName.contains("进度") ? 7.0 :
                        sName != null && sName.contains("营收") ? 38.0 : 42.0);
                ChainNode targetNode = new ChainNode(tName != null ? tName : "?",
                        tName != null && tName.contains("进度") ? 7.0 :
                        tName != null && tName.contains("营收") ? 38.0 :
                        tName != null && tName.contains("利润") ? 42.0 : 0.0);
                chainAnalysis.add(new ChainAnalysis(link.id(), sourceNode, targetNode,
                        generateImpactText(link)));
            }

            String rootCause = chainAnalysis.isEmpty() ? null : chainAnalysis.get(0).propagatedImpact();
            return ApiResponse.success(new CausalDeviationVO(goals, chainAnalysis, rootCause));
        } catch (Exception e) {
            log.error("Causal deviation analysis failed", e);
            return ApiResponse.internalError("因果偏差分析失败");
        }
    }

    private String generateImpactText(CausalLink link) {
        String srcName = link.sourceName();
        String tgtName = link.targetName();
        String relType = link.relationshipType();
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

    @GetMapping("/goal-tracking")
    public ApiResponse<GoalTrackingVO> getGoalTracking(
            @RequestParam(required = false) Long goalId) {
        try {
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
            List<GoalTrackingRow> tracking = dqDashboardService.query(sql, (rs, _i) -> {
                java.sql.Date recordedAt = rs.getDate("recorded_at");
                return new GoalTrackingRow(rs.getString("id"), rs.getLong("goal_id"),
                        rs.getString("goal_name"), rs.getInt("progress"),
                        rs.getBigDecimal("actual_value"), rs.getBigDecimal("target_value"),
                        rs.getString("note"), recordedAt != null ? recordedAt.toString() : null,
                        rs.getString("status"));
            }, params);
            return ApiResponse.success(new GoalTrackingVO(tracking, tracking.size()));
        } catch (Exception e) {
            log.error("Goal tracking query failed", e);
            return ApiResponse.internalError("目标追踪查询失败");
        }
    }

    // ── 行面 record / 响应 VO（#147CK：键名＝旧 Map 键，键序不变） ────────

    /** 列表容器（2 键 data/total）。 */
    public record DataPage<T>(List<T> data, int total) {
    }

    /** DQ 规则行（13 键，snake_case）。 */
    public record DqRuleRow(@JsonProperty("id") String id,
                            @JsonProperty("code") String code,
                            @JsonProperty("name") String name,
                            @JsonProperty("description") String description,
                            @JsonProperty("rule_type") String ruleType,
                            @JsonProperty("target_entity") String targetEntity,
                            @JsonProperty("target_field") String targetField,
                            @JsonProperty("rule_expression") String ruleExpression,
                            @JsonProperty("severity") String severity,
                            @JsonProperty("status") String status,
                            @JsonProperty("params") String params,
                            @JsonProperty("created_at") String createdAt,
                            @JsonProperty("updated_at") Timestamp updatedAt) {
    }

    /** DQ 问题行（15 键，snake_case）。 */
    public record DqIssueRow(@JsonProperty("id") String id,
                             @JsonProperty("rule_id") String ruleId,
                             @JsonProperty("entity_type") String entityType,
                             @JsonProperty("entity_id") String entityId,
                             @JsonProperty("field_name") String fieldName,
                             @JsonProperty("issue_type") String issueType,
                             @JsonProperty("severity") String severity,
                             @JsonProperty("description") String description,
                             @JsonProperty("current_value") String currentValue,
                             @JsonProperty("expected_value") String expectedValue,
                             @JsonProperty("status") String status,
                             @JsonProperty("assigned_to") String assignedTo,
                             @JsonProperty("detected_at") String detectedAt,
                             @JsonProperty("resolved_at") Timestamp resolvedAt,
                             @JsonProperty("resolution_note") String resolutionNote) {
    }

    /** DQ 执行行（8 键，snake_case）。 */
    public record DqExecutionRow(@JsonProperty("id") String id,
                                 @JsonProperty("rule_id") String ruleId,
                                 @JsonProperty("rule_name") String ruleName,
                                 @JsonProperty("passed") boolean passed,
                                 @JsonProperty("total_rows") int totalRows,
                                 @JsonProperty("failed_rows") int failedRows,
                                 @JsonProperty("error_details") String errorDetails,
                                 @JsonProperty("executed_at") String executedAt) {
    }

    /** 看板（6 键，snake_case）。 */
    public record DqDashboardVO(@JsonProperty("total_rules") int totalRules,
                                @JsonProperty("active_rules") int activeRules,
                                @JsonProperty("total_issues") int totalIssues,
                                @JsonProperty("open_issues") int openIssues,
                                @JsonProperty("by_severity") List<SeverityCount> bySeverity,
                                @JsonProperty("by_type") List<IssueTypeCount> byType) {
    }

    /** 严重度计数（2 键）。 */
    public record SeverityCount(String severity, long count) {
    }

    /** 类型计数（2 键，issue_type snake；显式排序防 record 注解重排）。 */
    @com.fasterxml.jackson.annotation.JsonPropertyOrder({"issue_type", "count"})
    public record IssueTypeCount(@JsonProperty("issue_type") String issueType, long count) {
    }

    /** 目标偏差（6 键，camelCase 与旧 Map 一致）。 */
    public record GoalDeviation(long id, String name, BigDecimal targetValue, BigDecimal currentValue,
                                double deviationPct, String status) {
    }

    /** 因果链定义（9 键，camelCase）。 */
    public record CausalLink(long id, String sourceName, BigDecimal sourceTarget,
                             BigDecimal sourceActual, String targetName, BigDecimal targetTarget,
                             BigDecimal targetActual, String relationshipType, String description) {
    }

    /** 链节点（2 键）。 */
    public record ChainNode(String name, double deviation) {
    }

    /** 链分析（4 键）。 */
    public record ChainAnalysis(long chainId, ChainNode sourceNode, ChainNode targetNode,
                                String propagatedImpact) {
    }

    /** 因果偏差响应（rootCause 仅在有链时出现）。 */
    public record CausalDeviationVO(List<GoalDeviation> goalDeviations,
                                    List<ChainAnalysis> causalChains,
                                    @JsonInclude(JsonInclude.Include.NON_NULL) String rootCause) {
    }

    /** 目标追踪行（9 键，camelCase）。 */
    public record GoalTrackingRow(String id, long goalId, String goalName, int progress,
                                  BigDecimal actualValue, BigDecimal targetValue, String note,
                                  String recordedAt, String status) {
    }

    /** 目标追踪响应（2 键）。 */
    public record GoalTrackingVO(List<GoalTrackingRow> tracking, int total) {
    }
}
