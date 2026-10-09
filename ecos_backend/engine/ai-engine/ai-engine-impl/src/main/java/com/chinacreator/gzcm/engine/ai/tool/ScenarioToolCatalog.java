package com.chinacreator.gzcm.engine.ai.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.chinacreator.gzcm.engine.ai.service.ToolSchema;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;

/**
 * 五个场景受控工具的<b>声明单源</b>（详细设计-06 F06-11；参数与后端映射逐条抄自 PRD-06 §4.1 表格，不得自创）。
 *
 * <p>与 {@code ecos_ai.ecos_ai_tool_registry}（V200）的关系：本目录是 V200 未实跑期间的<b>同构代码声明</b>，
 * 表就位后表内行覆盖本目录（见 {@link ScenarioToolRegistry}）。两者都只是声明 —— 没有任何
 * {@code Function}/{@code lambda} 执行体，故权限面完全可由 {@code ToolExecutorService} 的裁决载荷审计（X-12）。</p>
 *
 * <p><b>跨册接缝义务（§八）</b>：{@code forecast-runs} 族端点属分册 09 FC-02/FC-03，
 * {@code scenarios/{id}/actions} 属分册 07 N8 —— 三侧当前均未实现。本目录按<b>文档已定形的路径</b>声明，
 * 未实现时调用得到真实的 {@code TOOL_BACKEND_ERROR}（502），禁 mock 成功（X-50）。</p>
 */
public final class ScenarioToolCatalog {

    // ═══ 工具键（冻结字面量，与 PRD-06 §4.1 表头逐字一致） ═══
    public static final String FORECAST_QUERY = "forecast.query";
    public static final String FORECAST_DRILLDOWN = "forecast.drilldown";
    public static final String FORECAST_EXPLAIN = "forecast.explain";
    public static final String SCENARIO_COPY_COMPARE = "scenario.copyCompare";
    public static final String ACTION_CREATE_DRAFT = "action.createDraft";

    /** 分册 09 FC-02 预测运行服务（workspace 属主）。 */
    public static final String SERVICE_WORKSPACE = "workspace";
    /** 分册 05 COG-02 认知诊断（cognitive 属主）。 */
    public static final String SERVICE_COGNITIVE = "cognitive";

    /** 只读取数族（三个 forecast 工具）—— 禁复用 query_db（X-13）。 */
    public static final Set<String> READ_ONLY_DATA_TOOLS =
            Set.of(FORECAST_QUERY, FORECAST_DRILLDOWN, FORECAST_EXPLAIN);

    /**
     * 场景 Agent 白名单里<b>显式禁用</b>的通用工具（F06-11 要点 3 / X-46）。
     *
     * <p>白名单语义从"能力清单"升级为"经裁决的权限视图"：本集合与场景工具集取交集时直接判 DENY，
     * 不再依赖 security 侧是否有对应策略 —— 通用 {@code query_db}/{@code invoke_rest} 允许 LLM
     * 自由生成 SQL/URL，与"只经五个受控入口取数"的场景约束互斥。</p>
     */
    public static final Set<String> DENIED_FOR_SCENARIO_AGENTS = Set.of("query_db", "invoke_rest");

    private static final Map<String, ScenarioToolDefinition> TOOLS = build();

    private ScenarioToolCatalog() {}

    public static List<ScenarioToolDefinition> all() {
        return List.copyOf(TOOLS.values());
    }

    public static Set<String> keys() {
        return TOOLS.keySet();
    }

    public static ScenarioToolDefinition find(String toolKey) {
        return toolKey == null ? null : TOOLS.get(toolKey);
    }

    public static boolean isScenarioTool(String toolKey) {
        return TOOLS.containsKey(toolKey);
    }

    /** 该白名单是否属于场景 Agent（含任一场景工具即算，用于判定 DENIED_FOR_SCENARIO_AGENTS 是否生效）。 */
    public static boolean isScenarioWhitelist(List<String> whitelist) {
        if (whitelist == null) return false;
        for (String name : whitelist) {
            if (TOOLS.containsKey(name)) return true;
        }
        return false;
    }

    /** 场景 Agent 白名单合法性：不得同时出现被禁的通用工具（配置态红线，启动即判）。 */
    public static List<String> illegalEntriesForScenario(List<String> whitelist) {
        if (whitelist == null) return List.of();
        return whitelist.stream().filter(DENIED_FOR_SCENARIO_AGENTS::contains).toList();
    }

    /**
     * 写工具载荷收口（PRD-06 §4.1"仅创建 DRAFT 态、审批仍走人工流" + PMO-74 主体只从 token 取）：
     *
     * <ol>
     *   <li>{@code action.createDraft} 的 {@code status} 无条件置 {@code DRAFT}，禁 LLM 传入的终态值；</li>
     *   <li>身份/审批人字段（{@code approver}/{@code approvedBy}/{@code userId}/{@code tenantId}/
     *       {@code createdBy}/{@code operator}）从载荷中<b>剔除</b> —— 审批人只能由服务端从 token 主体取
     *       （分册 07 N10 {@code approver(from token)}），入参自报值即 X-82 的身份伪造面。</li>
     * </ol>
     */
    public static Map<String, Object> enforceWritePayload(ScenarioToolDefinition def,
                                                          Map<String, Object> arguments) {
        Map<String, Object> payload = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
        if (def == null || !def.isWrite()) return payload;
        if (ACTION_CREATE_DRAFT.equals(def.toolKey())) {
            payload.put("status", DRAFT_STATE);
            IDENTITY_FIELDS.forEach(payload::remove);
        }
        return payload;
    }

    /** 冻结字面量：动作草稿态唯一可创建值。 */
    public static final String DRAFT_STATE = "DRAFT";

    /** 只允许来自 token 主体的字段名（禁从工具入参自报）。 */
    public static final Set<String> IDENTITY_FIELDS =
            Set.of("approver", "approvedBy", "userId", "tenantId", "createdBy", "operator");

    // ═══ 声明体（逐条对齐 PRD-06 §4.1） ═══

    private static Map<String, ScenarioToolDefinition> build() {
        Map<String, ScenarioToolDefinition> map = new LinkedHashMap<>();

        // ① forecast.query — 预测运行服务 results 端点（PRD-09 FC-02）；只读，RLS 按部门域
        put(map, new ScenarioToolDefinition(
                FORECAST_QUERY, "预测运行结果查询",
                "按运行/情景/年度/层级读取年度经营预测结果，返回体自带引用元数据（运行 ID、口径@版本、截至时间、证据链接）。",
                params(
                        p("runId", false, "预测运行 ID（可选；缺省时须给 scenarioId 由后端定位最新运行）"),
                        p("scenarioId", false, "业务场景 ID"),
                        pi("year", true, "预测年度"),
                        pen("level", true, "汇总层级", List.of("enterprise", "dept", "project"))),
                ScenarioToolDefinition.BACKEND_KIND_REST, SERVICE_WORKSPACE,
                "/api/v1/workspace/forecast-runs/{runId}/results", "GET",
                ScenarioToolDefinition.OPERATION_READ, "forecast:read", 3_000,
                "runId,scenarioId,year,level"));

        // ② forecast.drilldown — results 下钻 + evidence 端点；只读，行级 RLS
        put(map, new ScenarioToolDefinition(
                FORECAST_DRILLDOWN, "预测结果下钻与证据",
                "读取指定运行、层级、实体的预测下钻明细及其证据链（口径版本与截至时间随引用元数据返回）。",
                params(
                        p("runId", true, "预测运行 ID"),
                        pen("level", true, "下钻层级", List.of("enterprise", "dept", "project")),
                        p("entityId", true, "下钻实体标识（部门/项目编码）"),
                        p("period", false, "期间（如 2026-Q1 / 2026-01）"),
                        p("stage", false, "计算阶段标识")),
                ScenarioToolDefinition.BACKEND_KIND_REST, SERVICE_WORKSPACE,
                "/api/v1/workspace/forecast-runs/{runId}/drilldown/{level}/{entityId}", "GET",
                ScenarioToolDefinition.OPERATION_READ, "forecast:read:row", 3_000,
                "runId,level,entityId,period,stage"));

        // ③ forecast.explain — cognitive diagnose（带 forecastContext，PRD-05 COG-02）；只读
        put(map, new ScenarioToolDefinition(
                FORECAST_EXPLAIN, "预测偏差认知解释",
                "就指定运行/项目/期间向认知引擎请求诊断解释（偏差归因与最大风险），只读、不产生数值。",
                params(
                        p("runId", true, "预测运行 ID"),
                        p("projectId", true, "被解释的项目/对象标识"),
                        p("period", true, "期间")),
                ScenarioToolDefinition.BACKEND_KIND_REST, SERVICE_COGNITIVE,
                CognitiveApiPaths.COGNITIVE_DIAGNOSE, "POST",
                ScenarioToolDefinition.OPERATION_READ, "cognitive:diagnose", 8_000,
                "runId,projectId,period"));

        // ④ scenario.copyCompare — 情景运行 + diff；写，OPA 需 scenario:write
        put(map, new ScenarioToolDefinition(
                SCENARIO_COPY_COMPARE, "情景复制对比运行",
                "以基线运行为模板创建情景副本并执行对比计算，返回新运行 ID 与差异集（写操作，须 scenario:write）。",
                params(
                        p("baselineRunId", true, "基线预测运行 ID"),
                        po("overrides", true, "结构化假设覆盖（同 PRD-05 COG-03 形态）")),
                ScenarioToolDefinition.BACKEND_KIND_REST, SERVICE_WORKSPACE,
                "/api/v1/workspace/forecast-runs/{baselineRunId}/scenario-copy", "POST",
                ScenarioToolDefinition.OPERATION_WRITE, "scenario:write", 10_000,
                "baselineRunId"));

        // ⑤ action.createDraft — 动作服务，仅创建 DRAFT 态；写，审批走人工流
        put(map, new ScenarioToolDefinition(
                ACTION_CREATE_DRAFT, "经营动作草稿",
                "就指定场景创建经营动作<b>草稿</b>（五必填：负责人/截止日/预期影响/控制指标 + 关联运行）。"
                        + "审批人只从 token 主体取，状态强制 DRAFT，审批仍走人工流。",
                params(
                        p("scenarioId", true, "业务场景 ID"),
                        p("owner", true, "负责人"),
                        p("dueDate", true, "截止日（ISO 日期）"),
                        p("expectedImpact", true, "预期影响（金额，取自工具结果的引用数值）"),
                        p("controlMetric", true, "控制指标"),
                        p("forecastRunId", false, "关联的预测运行 ID（溯源用）")),
                ScenarioToolDefinition.BACKEND_KIND_REST, SERVICE_WORKSPACE,
                "/api/v1/workspace/scenarios/{scenarioId}/actions", "POST",
                ScenarioToolDefinition.OPERATION_WRITE, "action:draft", 5_000,
                "scenarioId,forecastRunId,controlMetric"));

        return map;
    }

    private static void put(Map<String, ScenarioToolDefinition> map, ScenarioToolDefinition def) {
        map.put(def.toolKey(), def);
    }

    // ── 参数声明辅助 ──

    /** 参数名 + 定义的成对载体（声明顺序即 LLM 看到的属性顺序）。 */
    private record Arg(String name, ToolSchema.ParamDef def) {}

    private static Map<String, ToolSchema.ParamDef> params(Arg... args) {
        Map<String, ToolSchema.ParamDef> map = new LinkedHashMap<>();
        for (Arg arg : args) map.put(arg.name(), arg.def());
        return map;
    }

    private static Arg p(String name, boolean required, String desc) {
        return new Arg(name, new ToolSchema.ParamDef("string", required, desc));
    }

    private static Arg pi(String name, boolean required, String desc) {
        return new Arg(name, new ToolSchema.ParamDef("integer", required, desc));
    }

    private static Arg pen(String name, boolean required, String desc, List<String> enumValues) {
        ToolSchema.ParamDef def = new ToolSchema.ParamDef("string", required, desc);
        def.setEnumValues(enumValues);
        return new Arg(name, def);
    }

    private static Arg po(String name, boolean required, String desc) {
        return new Arg(name, new ToolSchema.ParamDef("object", required, desc));
    }
}
