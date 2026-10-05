package com.chinacreator.gzcm.ai.wagent.tool;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolCategory;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 分册10 F10-17 · 业务工具目录：附册 §4 列出的 12 个种子工具（INITIAL_TWELVE），
 * 每个 endpointUrl 均落在 {@code WAgentEngineCatalog.ENGINES} 既有前缀之内
 * （F10-02/W245：工具调用必走既有引擎端点，禁未知路径）。
 *
 * <p>全部 {@code endpointKind="rest"}、status=ACTIVE（种子已发）。
 * 金额/效果类用 String，禁 double/float（数值只 BigDecimal 或 String）。</p>
 */
public final class BusinessToolCatalog {

    private BusinessToolCatalog() {}

    /** 基线：12 个种子工具（附册 §4 冻结 + API 只增不改，禁覆盖/删减）。 */
    public static final List<ToolContract> INITIAL_TWELVE = List.of(
        build("query_project_profit",          ToolCategory.QUERY,    1, "data-engine",        "/api/v1/data/query"),                 //  1
        build("get_project_acceptance_status", ToolCategory.QUERY,    1, "data-engine",        "/api/v1/data/query"),                 //  2
        build("run_profit_forecast",           ToolCategory.ANALYZE,  2, "cognitive-engine",   "/api/v1/cognitive/forecast"),         //  3
        build("find_historical_profile",       ToolCategory.QUERY,    1, "kb-engine",          "/api/v1/kb/graph/query"),             //  4
        build("run_cognitive_model",           ToolCategory.ANALYZE,  2, "cognitive-engine",   "/api/v1/cognitive/models"),           //  5
        build("create_decision_option",        ToolCategory.COMMIT,   3, "ai-engine (aiming)", "/api/v1/wagent/decisions"),           //  6
        build("create_action_draft",           ToolCategory.COMMIT,   3, "ai-engine (aiming)", "/api/v1/wagent/actions"),             //  7
        build("get_action_effect",             ToolCategory.QUERY,    1, "data-engine",        "/api/v1/data/query"),                 //  8
        build("check_metric_caliber",          ToolCategory.QUERY,    1, "ontology-engine",    "/api/v1/ontology/entity-mappings"),   // 9
        build("select_knowledge_profiles",     ToolCategory.QUERY,    1, "kb-engine",          "/api/v1/kb/rules"),                   // 10
        build("freeze_data_snapshot",          ToolCategory.COMMIT,   2, "data-engine",        "/api/v1/data/datalake"),              // 11
        build("submit_readiness_task",         ToolCategory.COMMIT,   2, "ai-engine (aiming)", "/api/v1/wagent/runs")                  // 12
    );

    /** 便捷：全部 12 工具名稳定序集合（parity/coverage 断言用；新增工具时保持 append-only）。 */
    public static Set<String> toolNames() {
        Set<String> names = new LinkedHashSet<>();
        for (ToolContract tc : INITIAL_TWELVE) names.add(tc.name());
        return names;
    }

    /** 便捷：单个契约（找不到 ⇒ null，调用方判空）。 */
    public static ToolContract byName(String name) {
        if (name == null) return null;
        for (ToolContract tc : INITIAL_TWELVE) if (tc.name().equals(name)) return tc;
        return null;
    }

    /** 种子装配：强类型默认 + validateDefaults 双校（COMMIT 自动带上 rollbackPlanText）。 */
    private static ToolContract build(String name, ToolCategory cat, int minLevel,
                                      String engine, String endpointUrl) {
        String sideEffect = (cat == ToolCategory.COMMIT) ? "write" : "read";
        String rollback = (cat == ToolCategory.COMMIT) ? "rollback_" + name + "_v1" : null;
        return ToolContract.validateDefaults(new ToolContract(
                name, "1", engine, cat, sideEffect, minLevel,
                "perm:" + cat.name().toLowerCase() + ":" + name,
                "{\"type\":\"object\",\"properties\":{}}",
                "{\"type\":\"object\",\"properties\":{}}",
                30_000, cat == ToolCategory.QUERY,
                "cost:" + name, cat == ToolCategory.ANALYZE,
                "L1_internal", rollback,
                "rest", endpointUrl, "POST",
                "ai-engine", "sla_p95_30s", ToolStatus.ACTIVE, null, "business",
                "seed:" + name, "disclosure:" + name, "dep:" + name));
    }
}
