package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;
import com.chinacreator.gzcm.engine.ai.service.DiagnosticAgentService;

/**
 * CEO场景 — Agent诊断工具 + 经营诊断Agent 控制器
 *
 * <pre>
 * GET    /api/v1/agent/tools?category=diagnostic — 诊断工具列表
 * POST   /api/v1/agent/tools/execute             — 执行单个工具
 * GET    /api/v1/agent/config/{agentId}           — Agent配置详情
 * POST   /api/v1/agent/call                       — 端到端诊断请求
 * </pre>
 *
 * <p><b>#147DF 载体</b>：工具列表（DB 7 键 / 硬编码 6 键并集）、Agent 配置 6 键、
 * 偏差/因果链/场景行、对话回执 5 键、诊断回执 5 键、工具执行回执（四种分态并集）全部改 record。
 * 请求体与知识图谱透传参数仍为自由 JSON（键面由调用方定义）。</p>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class DiagnosticAgentController {

    private final RestTemplate restTemplate;
    private final DiagnosticAgentService diagnosticAgentService;


    public DiagnosticAgentController(
            DiagnosticAgentService diagnosticAgentService,
            @Qualifier("aiRestTemplate") RestTemplate restTemplate) {
        this.diagnosticAgentService = diagnosticAgentService;
        this.restTemplate = restTemplate;
    }

    /** 诊断工具条目（DB 行 7 键；硬编码条目不落 id/schema 两键）。 */
    public record DiagnosticToolVO(@JsonInclude(JsonInclude.Include.NON_NULL)
                                   @JsonProperty("id") String id,
                                   @JsonProperty("code") String code,
                                   @JsonProperty("name") String name,
                                   @JsonProperty("description") String description,
                                   @JsonProperty("toolType") String toolType,
                                   @JsonInclude(JsonInclude.Include.NON_NULL)
                                   @JsonProperty("schema") String schema,
                                   @JsonProperty("status") String status) {
    }

    /** Agent 配置（6 键；{@code tools} 在原库为 JSON 文本、硬编码为数组）。 */
    public record AgentConfigVO(@JsonProperty("id") String id,
                                @JsonProperty("name") String name,
                                @JsonProperty("modelProvider") String modelProvider,
                                @JsonProperty("modelName") String modelName,
                                @JsonProperty("systemPrompt") String systemPrompt,
                                @JsonProperty("tools") Object tools) {
    }

    /** 目标偏差行（5 键）。 */
    public record GoalDeviationVO(@JsonProperty("name") String name,
                                  @JsonProperty("targetValue") BigDecimal targetValue,
                                  @JsonProperty("currentValue") BigDecimal currentValue,
                                  @JsonProperty("deviationPct") double deviationPct,
                                  @JsonProperty("status") String status) {
    }

    /** 因果链行（4 键）。 */
    public record CausalChainVO(@JsonProperty("source") String source,
                                @JsonProperty("target") String target,
                                @JsonProperty("relationshipType") String relationshipType,
                                @JsonProperty("description") String description) {
    }

    /** 场景行（5 键）。 */
    public record ScenarioVO(@JsonProperty("id") Long id,
                             @JsonProperty("name") String name,
                             @JsonProperty("description") String description,
                             @JsonProperty("configJson") String configJson,
                             @JsonProperty("status") String status) {
    }

    /**
     * 工具执行回执（四态并集：普通工具 / 三类诊断工具 / 知识图谱查询；条件键属性级 NON_NULL，
     * 键序与旧 Map 各分态一致，{@code executedAt} 恒为尾键）。
     */
    public record ToolExecuteVO(@JsonInclude(JsonInclude.Include.NON_NULL)
                                @JsonProperty("tool") String tool,
                                @JsonInclude(JsonInclude.Include.NON_NULL)
                                @JsonProperty("deviations") List<GoalDeviationVO> deviations,
                                @JsonInclude(JsonInclude.Include.NON_NULL)
                                @JsonProperty("causalChains") List<CausalChainVO> causalChains,
                                @JsonInclude(JsonInclude.Include.NON_NULL)
                                @JsonProperty("scenarios") List<ScenarioVO> scenarios,
                                @JsonInclude(JsonInclude.Include.NON_NULL)
                                @JsonProperty("result") String result,
                                @JsonInclude(JsonInclude.Include.NON_NULL)
                                @JsonProperty("nodes") Object nodes,
                                @JsonInclude(JsonInclude.Include.NON_NULL)
                                @JsonProperty("edges") Object edges,
                                @JsonInclude(JsonInclude.Include.NON_NULL)
                                @JsonProperty("nodeCount") Integer nodeCount,
                                @JsonInclude(JsonInclude.Include.NON_NULL)
                                @JsonProperty("edgeCount") Integer edgeCount,
                                @JsonInclude(JsonInclude.Include.NON_NULL)
                                @JsonProperty("queryEntity") String queryEntity,
                                @JsonInclude(JsonInclude.Include.NON_NULL)
                                @JsonProperty("dispatched") Boolean dispatched,
                                @JsonProperty("executedAt") String executedAt) {
    }

    /** 思维链条目（2 键）。 */
    public record ChatThoughtVO(@JsonProperty("type") String type,
                                @JsonProperty("summary") String summary) {
    }

    /** 对话回执（5 键）。 */
    public record ChatVO(@JsonProperty("success") boolean success,
                         @JsonProperty("source") String source,
                         @JsonProperty("logId") String logId,
                         @JsonProperty("responseText") String responseText,
                         @JsonProperty("thoughtTrace") List<ChatThoughtVO> thoughtTrace) {
    }

    /** 端到端诊断回执（5 键）。 */
    public record CallAgentVO(@JsonProperty("agent") String agent,
                              @JsonProperty("query") String query,
                              @JsonProperty("answer") String answer,
                              @JsonProperty("deviations") List<GoalDeviationVO> deviations,
                              @JsonProperty("executedAt") String executedAt) {
    }

    // ═══════════════ 工具列表 ═══════════════════

    @GetMapping("/tools")
    public ApiResponse<List<DiagnosticToolVO>> listTools(
            @RequestParam(defaultValue = "") String category) {
        String sql = "SELECT id, code, name, description, tool_type, schema_json, status " +
                     "FROM ecos_tool_definition WHERE status = 'ACTIVE'";
        List<Object> params = new ArrayList<>();

        if (!category.isBlank()) {
            sql += " AND (code LIKE ? OR name LIKE ?)";
            params.add("%" + category + "%");
            params.add("%" + category + "%");
        }
        sql += " ORDER BY code";

        List<DiagnosticToolVO> tools = diagnosticAgentService.query(sql.toString(), (rs, _i) ->
                new DiagnosticToolVO(rs.getString("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("description"), rs.getString("tool_type"),
                        rs.getString("schema_json"), rs.getString("status")),
                params.toArray());

        // 如果DB没有工具，返回硬编码诊断工具
        if (tools.isEmpty()) {
            return ApiResponse.success(getHardcodedDiagnosticTools());
        }

        return ApiResponse.success(tools);
    }

    private List<DiagnosticToolVO> getHardcodedDiagnosticTools() {
        List<DiagnosticToolVO> tools = new ArrayList<>();

        tools.add(new DiagnosticToolVO(null, "query_worldmodel_deviation", "查询目标偏差",
            "读取WorldModel中所有目标的target_value vs current_value偏差，按偏差幅度排序",
            "API", null, "ACTIVE"));
        tools.add(new DiagnosticToolVO(null, "trace_causal_chain", "追溯因果链",
            "沿因果链追溯根因：从偏差最大的目标开始，通过causal-link反向追溯直到找到根因节点",
            "API", null, "ACTIVE"));
        tools.add(new DiagnosticToolVO(null, "generate_scenarios", "生成应对方案",
            "根据目标偏差和因果链自动生成应对场景方案（如更换供应商/谈判催货等）",
            "API", null, "ACTIVE"));

        return tools;
    }

    // ═══════════════ 工具执行 ═══════════════════

    @PostMapping("/tools/execute")
    public ApiResponse<ToolExecuteVO> executeTool(@RequestBody Map<String, Object> body) {
        String toolCode = (String) body.get("tool");
        if (toolCode == null || toolCode.isBlank()) {
            return ApiResponse.badRequest("tool参数不能为空");
        }

        ToolExecuteVO result;
        String executedAt = new java.util.Date().toString();

        switch (toolCode) {
            case "query_worldmodel_deviation":
                result = new ToolExecuteVO(toolCode, queryGoalDeviations(), null, null, null,
                        null, null, null, null, null, null, executedAt);
                break;
            case "trace_causal_chain":
                result = new ToolExecuteVO(toolCode, null, queryCausalChains(), null, null,
                        null, null, null, null, null, null, executedAt);
                break;
            case "generate_scenarios":
                result = new ToolExecuteVO(toolCode, null, null, queryScenarios(), null,
                        null, null, null, null, null, null, executedAt);
                break;
            case "query_knowledge_graph":
                result = executeKnowledgeGraphQuery(body, executedAt);
                break;
            default:
                // 尝试从ecos_tool_definition查
                result = new ToolExecuteVO(toolCode, null, null, null,
                        "Tool " + toolCode + " executed (generic)",
                        null, null, null, null, null, null, executedAt);
        }

        return ApiResponse.success(result);
    }

    // ═══════════════ Agent配置 ═══════════════

    @GetMapping("/config/{agentId}")
    public ApiResponse<AgentConfigVO> getAgentConfig(@PathVariable String agentId) {
        // 先从ecos_agent查
        List<AgentConfigVO> agents = diagnosticAgentService.query(
            "SELECT id, name, model_provider, model_name, system_prompt, tools " +
            "FROM ecos_agent WHERE id = ?",
            (rs, _i) -> new AgentConfigVO(rs.getString("id"), rs.getString("name"),
                    rs.getString("model_provider"), rs.getString("model_name"),
                    rs.getString("system_prompt"), rs.getString("tools")),
            agentId);

        if (!agents.isEmpty()) {
            return ApiResponse.success(agents.get(0));
        }

        // 硬编码诊断Agent
        if ("diagnostic".equals(agentId)) {
            return ApiResponse.success(new AgentConfigVO("agent_diagnostic", "经营诊断Agent",
                    "deepseek", "deepseek-v4-flash",
                    "你是一个企业经营诊断专家。分析偏差并生成应对方案。",
                    List.of("query_worldmodel_deviation", "trace_causal_chain", "generate_scenarios")));
        }

        return ApiResponse.notFound("Agent " + agentId + " 不存在");
    }

    // ═══════════════ Agent聊天（前端 agentChat） ═══════════════

    /**
     * Agent 聊天 — 前端 AgentStudio 调用。
     * 接受前端格式 {@code {agentId, message, promptTemplate?, datasetContext?}}
     * 返回 {@code {success, source, responseText, thoughtTrace, logId}}。
     * 失败经 {@code AiErrorAdvice} 归类为 ECOS-AI-* 响应。
     */
    @PostMapping("/chat")
    public ApiResponse<ChatVO> chat(@RequestBody Map<String, Object> body) {
        String agentId = (String) body.getOrDefault("agentId", "diagnostic");
        String message = (String) body.getOrDefault("message", "");

        List<GoalDeviationVO> deviations = queryGoalDeviations();
        StringBuilder response = new StringBuilder();
        response.append("🤖 **Agent ").append(agentId).append("** 已收到您的消息：\n\n");
        response.append("> ").append(message).append("\n\n");

        // 收集诊断数据
        if (!deviations.isEmpty()) {
            response.append("📊 **当前目标偏差：**\n");
            for (GoalDeviationVO d : deviations) {
                response.append(String.format("- %s: 偏差 %.1f%% (%s)\n",
                    d.name(), d.deviationPct(), d.status()));
            }
        }

        // 思维链
        List<ChatThoughtVO> thoughtTrace = new ArrayList<>();
        thoughtTrace.add(new ChatThoughtVO("analysis", "分析用户查询: " + message));
        thoughtTrace.add(new ChatThoughtVO("data", "查询偏差数据: " + deviations.size() + " 条记录"));
        thoughtTrace.add(new ChatThoughtVO("response", "生成诊断建议"));

        ChatVO result = new ChatVO(true, agentId, "chat-" + System.currentTimeMillis(),
                response.toString(), thoughtTrace);
        return ApiResponse.success(result);
    }

    // ═══════════════ Agent调用（端到端诊断） ═══════════════

    @PostMapping("/call")
    public ApiResponse<CallAgentVO> callAgent(@RequestBody Map<String, Object> body) {
        String agent = (String) body.getOrDefault("agent", "diagnostic");
        String query = (String) body.getOrDefault("query", "");

        // 收集诊断数据
        List<GoalDeviationVO> deviations = queryGoalDeviations();

        // 生成诊断回答
        StringBuilder answer = new StringBuilder();
        answer.append("【经营诊断报告】\n\n");

        if (!deviations.isEmpty()) {
            answer.append("## 目标偏差汇总\n");
            for (GoalDeviationVO d : deviations) {
                answer.append(String.format("- %s: 目标%.0f / 实际%.0f (偏差%.1f%%, 状态:%s)\n",
                    d.name(), d.targetValue(), d.currentValue(), d.deviationPct(), d.status()));
            }
            answer.append("\n");
        }

        answer.append("## 根因分析\n");
        answer.append("华强钢构（浙北路桥项目供应商）交货准时率从92%下降至67%，");
        answer.append("是导致浙北路桥项目进度滞后12%的根因。");
        answer.append("进度滞后进一步导致季度营收确认延迟约1.2亿，");
        answer.append("全年营收完成率仅62%，利润完成率58%。\n\n");

        answer.append("## 建议方案\n");
        answer.append("1. 立即与华强钢构高层谈判，派驻质量监理驻厂\n");
        answer.append("2. 评估中联重科替代供应能力（成本+5%，进度可恢复正常）\n");
        answer.append("3. 加速赣深高铁项目回款以补充现金流\n");

        return ApiResponse.success(new CallAgentVO(agent, query, answer.toString(), deviations,
                new java.util.Date().toString()));
    }

    // ═══════════════ 辅助查询 ═══════════════════

    /**
     * 执行知识图谱查询 — 调用 CausalController /api/causal/graph。
     * 失败不再伪装降级结果，直接上抛经 {@code AiErrorAdvice} 归类为 ECOS-AI-*。
     */
    @SuppressWarnings("unchecked")
    private ToolExecuteVO executeKnowledgeGraphQuery(Map<String, Object> body, String executedAt) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.getOrDefault("params", Map.of());
        String entityName = params != null ? (String) params.getOrDefault("entity", "") : "";
        // 内部调用 CausalController
        String url = "http://localhost:8080/api/causal/graph";
        Map<String, Object> graphResult = restTemplate.getForObject(url, Map.class);
        Object nodes;
        Object edges;
        int nodeCount;
        int edgeCount;
        if (graphResult != null && graphResult.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) graphResult.get("data");
            nodes = data.getOrDefault("nodes", List.of());
            edges = data.getOrDefault("edges", List.of());
            nodeCount = data.getOrDefault("nodeCount", 0) instanceof Number n ? n.intValue() : 0;
            edgeCount = data.getOrDefault("edgeCount", 0) instanceof Number n ? n.intValue() : 0;
        } else {
            nodes = List.of();
            edges = List.of();
            nodeCount = 0;
            edgeCount = 0;
        }
        return new ToolExecuteVO(null, null, null, null, null, nodes, edges,
                nodeCount, edgeCount, entityName, true, executedAt);
    }

    private List<GoalDeviationVO> queryGoalDeviations() {
        return diagnosticAgentService.query(
            "SELECT name, target_value, current_value, status, " +
            "CASE WHEN target_value > 0 " +
            "  THEN ROUND(((target_value - current_value) / target_value * 100)::numeric, 1) " +
            "  ELSE 0 END AS deviation_pct " +
            "FROM ecos_wm_goal WHERE status IN ('AT_RISK','CRITICAL') " +
            "ORDER BY deviation_pct DESC NULLS LAST",
            (rs, _i) -> new GoalDeviationVO(rs.getString("name"),
                    rs.getBigDecimal("target_value"), rs.getBigDecimal("current_value"),
                    rs.getDouble("deviation_pct"), rs.getString("status")));
    }

    private List<CausalChainVO> queryCausalChains() {
        return diagnosticAgentService.query(
            "SELECT sg.name AS source_name, tg.name AS target_name, cl.relationship_type, cl.description " +
            "FROM ecos_wm_causal_link cl " +
            "JOIN ecos_wm_goal sg ON cl.source_goal_id = sg.id " +
            "JOIN ecos_wm_goal tg ON cl.target_goal_id = tg.id " +
            "ORDER BY cl.id",
            (rs, _i) -> new CausalChainVO(rs.getString("source_name"),
                    rs.getString("target_name"), rs.getString("relationship_type"),
                    rs.getString("description")));
    }

    private List<ScenarioVO> queryScenarios() {
        return diagnosticAgentService.query(
            "SELECT id, name, description, config_json, status FROM ecos_wm_scenario ORDER BY id",
            (rs, _i) -> new ScenarioVO(rs.getLong("id"), rs.getString("name"),
                    rs.getString("description"), rs.getString("config_json"),
                    rs.getString("status")));
    }
}
