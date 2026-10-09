package com.chinacreator.gzcm.engine.ai.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-11（REQ-AI-03）验收 —— 五个场景受控工具的声明单源。
 *
 * <p>doc 冻结的三个标识：{@code fiveToolsRegisteredWithBackendEndpoints}、
 * {@code writeToolsDeclareScenarioWrite}、{@code forecastToolsNeverRouteThroughQueryDb}。</p>
 *
 * <p>钉住的三条不变式：</p>
 * <ul>
 *   <li><b>声明式、无执行体（X-12）</b>：每条声明只有后端映射，没有任何 Java lambda；
 *       后端形态只允许 REST，故注册表面不可能长出"任意 SQL"的工具。</li>
 *   <li><b>三读数工具禁 query_db（X-13）</b>：读数只经 forecast.query/drilldown/explain 三个端点，
 *       通用 {@code query_db}/{@code invoke_rest} 在场景白名单里判非法。</li>
 *   <li><b>写只到 DRAFT（PRD-06 §4.1）</b>：写工具的 operation/permissionHint 必须显式声明，
 *       不由参数推断。</li>
 * </ul>
 */
class ScenarioToolRegistryTest {

    /** 表未就位时的读入口（provider=null → 只读代码声明单源）。 */
    private final ScenarioToolRegistry registry = new ScenarioToolRegistry(null);

    @Test
    @DisplayName("F06-11 要点 1：五个受控工具全部在册，每条声明都带后端端点且无执行体")
    void fiveToolsRegisteredWithBackendEndpoints() {
        List<ScenarioToolDefinition> declarations = registry.declarations();

        assertEquals(5, declarations.size(),
                "受控工具必须恰是 PRD-06 §4.1 的五个，不多不少: " + names(declarations));
        assertTrue(declarations.containsAll(List.of(
                        ScenarioToolCatalog.find(ScenarioToolCatalog.FORECAST_QUERY),
                        ScenarioToolCatalog.find(ScenarioToolCatalog.FORECAST_DRILLDOWN),
                        ScenarioToolCatalog.find(ScenarioToolCatalog.FORECAST_EXPLAIN),
                        ScenarioToolCatalog.find(ScenarioToolCatalog.SCENARIO_COPY_COMPARE),
                        ScenarioToolCatalog.find(ScenarioToolCatalog.ACTION_CREATE_DRAFT))),
                "在册声明必须来自同一目录单源");

        for (ScenarioToolDefinition def : declarations) {
            assertNotNull(def.backendEndpoint(), "声明必须带后端端点: " + def.toolKey());
            assertFalse(def.backendEndpoint().isBlank(), "端点不得为空: " + def.toolKey());
            assertTrue(def.backendEndpoint().startsWith("/"),
                    "端点必须是服务端相对路径（绝对 URL 会把基址交给调用方）: " + def.toolKey());
            assertEquals(ScenarioToolDefinition.BACKEND_KIND_REST, def.backendKind(),
                    "后端形态只允许 REST（X-12/X-13）: " + def.toolKey());
            assertFalse(def.argsSchema().isEmpty(), "声明必须带参数模式: " + def.toolKey());
            assertTrue(def.timeoutMs() > 0 && def.timeoutMs() <= ScenarioToolDefinition.MAX_TIMEOUT_MS,
                    "超时必须有上界: " + def.toolKey());
            // 声明即 LLM 可见的工具目录形态（function-calling schema），无需另建注册通道；
            // LLM 只能按 toolKey 调用（展示名进不了 schema，避免"按中文名调用"的第二套名字）
            Map<String, Object> schema = def.toFunctionCallSchema();
            assertEquals(def.toolKey(), schema.get("name"), "schema 名必须等于在册 toolKey: " + def.toolKey());
            assertNotNull(def.name(), "展示名单列（供目录面板），不得混进调用名: " + def.toolKey());
        }

        // 逐条抄自 PRD-06 §4.1 的映射（端点即契约，改一个字都要过评审）
        assertEquals("/api/v1/workspace/forecast-runs/{runId}/results",
                require(ScenarioToolCatalog.FORECAST_QUERY).backendEndpoint());
        assertEquals("/api/v1/workspace/forecast-runs/{runId}/drilldown/{level}/{entityId}",
                require(ScenarioToolCatalog.FORECAST_DRILLDOWN).backendEndpoint());
        assertEquals("/api/v1/workspace/forecast-runs/{baselineRunId}/scenario-copy",
                require(ScenarioToolCatalog.SCENARIO_COPY_COMPARE).backendEndpoint());
        assertEquals("/api/v1/workspace/scenarios/{scenarioId}/actions",
                require(ScenarioToolCatalog.ACTION_CREATE_DRAFT).backendEndpoint());
    }

    @Test
    @DisplayName("F06-11 要点 2：两个写工具显式声明 write + scenario:write/action:draft，读数工具声明 read")
    void writeToolsDeclareScenarioWrite() {
        ScenarioToolDefinition copyCompare = require(ScenarioToolCatalog.SCENARIO_COPY_COMPARE);
        ScenarioToolDefinition createDraft = require(ScenarioToolCatalog.ACTION_CREATE_DRAFT);

        assertEquals(ScenarioToolDefinition.OPERATION_WRITE, copyCompare.operation());
        assertEquals(ScenarioToolDefinition.OPERATION_WRITE, createDraft.operation());
        assertEquals("scenario:write", copyCompare.permissionHint());
        assertEquals("action:draft", createDraft.permissionHint());
        assertTrue(copyCompare.isWrite() && createDraft.isWrite());

        for (String readKey : List.of(ScenarioToolCatalog.FORECAST_QUERY,
                ScenarioToolCatalog.FORECAST_DRILLDOWN, ScenarioToolCatalog.FORECAST_EXPLAIN)) {
            ScenarioToolDefinition def = require(readKey);
            assertEquals(ScenarioToolDefinition.OPERATION_READ, def.operation(), readKey);
            assertFalse(def.isWrite(), "读数工具不得声明为写: " + readKey);
        }

        // 写载荷收口：状态强制 DRAFT、身份字段剔除（审批人只从 token 主体取）
        Map<String, Object> payload = ScenarioToolCatalog.enforceWritePayload(createDraft,
                new java.util.LinkedHashMap<>(Map.of(
                        "scenarioId", "s-1",
                        "status", "APPROVED",
                        "approver", "someone",
                        "owner", "lisi")));
        assertEquals(ScenarioToolCatalog.DRAFT_STATE, payload.get("status"),
                "写工具不得接受模型给的终态: " + payload);
        assertFalse(payload.containsKey("approver"), "身份字段必须由服务端主体决定: " + payload);
        assertEquals("lisi", payload.get("owner"), "非身份字段原样透传");
    }

    @Test
    @DisplayName("F06-11 要点 3（X-13/X-46）：三读数工具永不走 query_db，通用兜底工具在场景侧判非法")
    void forecastToolsNeverRouteThroughQueryDb() {
        List<String> readTools = List.of(ScenarioToolCatalog.FORECAST_QUERY,
                ScenarioToolCatalog.FORECAST_DRILLDOWN, ScenarioToolCatalog.FORECAST_EXPLAIN);

        for (String key : readTools) {
            ScenarioToolDefinition def = require(key);
            assertEquals(ScenarioToolDefinition.BACKEND_KIND_REST, def.backendKind(),
                    "读数只经声明的 REST 端点，禁 SQL 直查他引擎表: " + key);
            for (String arg : def.argsSchema().keySet()) {
                assertFalse(arg.toLowerCase(java.util.Locale.ROOT).contains("sql"),
                        "读数参数里不得出现 sql 形态入参: " + key + "." + arg);
            }
            assertFalse(def.backendEndpoint().contains("query_db"), key);
        }

        assertEquals(3, ScenarioToolCatalog.READ_ONLY_DATA_TOOLS.size(),
                "读数三工具的单源清单: " + ScenarioToolCatalog.READ_ONLY_DATA_TOOLS);
        assertTrue(ScenarioToolCatalog.READ_ONLY_DATA_TOOLS.containsAll(readTools));

        // 通用兜底工具在场景白名单里非法（启动自检 + 咽喉二道门）
        assertTrue(ScenarioToolCatalog.DENIED_FOR_SCENARIO_AGENTS.contains("query_db"));
        assertTrue(ScenarioToolCatalog.DENIED_FOR_SCENARIO_AGENTS.contains("invoke_rest"));
        assertEquals(List.of("query_db"),
                ScenarioToolCatalog.illegalEntriesForScenario(
                        List.of(ScenarioToolCatalog.FORECAST_QUERY, "query_db")),
                "白名单自检必须点名被禁项");
        assertTrue(ScenarioToolCatalog.isScenarioWhitelist(
                List.of(ScenarioToolCatalog.FORECAST_QUERY, ScenarioToolCatalog.ACTION_CREATE_DRAFT)));
        assertFalse(ScenarioToolCatalog.isScenarioWhitelist(List.of("query_db")),
                "纯通用白名单不属场景 Agent");

        // 注册表只给声明，执行归咽喉；通用工具不在册
        assertEquals(Optional.empty(), registry.find("query_db"),
                "通用工具不得出现在受控声明目录里");
    }

    private ScenarioToolDefinition require(String toolKey) {
        return registry.find(toolKey).orElseThrow(
                () -> new AssertionError("受控工具未在册: " + toolKey));
    }

    private static List<String> names(List<ScenarioToolDefinition> declarations) {
        return declarations.stream().map(ScenarioToolDefinition::toolKey).toList();
    }
}
