package com.chinacreator.gzcm.engine.cognitive2.registry;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive2.ForecastService;
import com.chinacreator.gzcm.engine.cognitive2.controller.ForecastController;
import com.chinacreator.gzcm.engine.cognitive2.dao.ModelRegistryDao;
import com.chinacreator.gzcm.engine.cognitive2.service.ModelPublishGateEvaluator;
import com.chinacreator.gzcm.engine.cognitive2.service.ModelRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #146b-C（P0-8 Readiness 供数）行为冻结：{@code GET /api/v1/cognitive/models}
 * additive 扩参（可选 {@code registryState} / {@code cognitiveModelType} 过滤）+ 响应行
 * additive 加键（{@code backtestGate} / {@code backtestScore}）。
 *
 * <p>裁决面 = WAgent Readiness 两项度量可由既有端点直接判定：
 * <ul>
 *   <li>{@code cog.cm_ready} = {@code ?registryState=PUBLISHED&cognitiveModelType=CM-xx} 命中行 &gt; 0
 *       （模型类型在册 ∧ 发布态）；</li>
 *   <li>{@code cog.backtest_ready} = 命中行 {@code backtestGate}（gate_backtest 列原值透传）= PASS。</li>
 * </ul>
 * DAO 以 Mockito 替身注入（同 {@code CognitiveModelRegistryTest} 基建，测试不触库）；
 * 库侧真实 SQL 行为依赖 V261 迁移实跑。</p>
 */
class ModelsReadinessFilterTest {

    private ModelRegistryDao dao;
    private ModelRegistryService service;
    private ForecastController controller;

    @BeforeEach
    void setUp() {
        dao = mock(ModelRegistryDao.class);
        service = new ModelRegistryService(dao, new ModelPublishGateEvaluator());
        controller = new ForecastController(mock(ForecastService.class), service);
    }

    // ── 用例 1：registryState=PUBLISHED 只回发布行（供数全链 Controller→Service→DAO）──

    @Test
    @DisplayName("registryStatePublishedOnlyReturnsPublishedRows：PUBLISHED 过滤下推 DAO 新读面，命中行原样返回")
    void registryStatePublishedOnlyReturnsPublishedRows() {
        when(dao.listByTypeAndState(eq("FORECAST"), eq("PUBLISHED"), isNull()))
                .thenReturn(List.of(modelRow("r-pub", "PUBLISHED", "PASS", new BigDecimal("0.87"))));

        ApiResponse<List<Map<String, Object>>> resp =
                controller.listModels("FORECAST", "PUBLISHED", null);

        assertEquals(1, resp.getData().size(),
                "PUBLISHED 过滤必须走 DAO 新读面下推（旧 listModels 单参链不适用）");
        assertEquals("PUBLISHED", resp.getData().get(0).get("registry_state"),
                "只回发布行：DRAFT 行不得混入（cm_ready 判定假绿面）");
        verify(dao).listByTypeAndState("FORECAST", "PUBLISHED", null);
        verify(dao, never()).listModels("FORECAST");
    }

    @Test
    @DisplayName("legacyPathWhenFiltersOmitted：新参数不传 ⇒ 旧 listModels(modelType) 单参链，行为字节等价")
    void legacyPathWhenFiltersOmitted() {
        when(dao.listModels("FORECAST")).thenReturn(List.of(modelRow("r1", "DRAFT", "PENDING", null)));

        ApiResponse<List<Map<String, Object>>> resp = controller.listModels("FORECAST", null, null);

        assertEquals(1, resp.getData().size());
        verify(dao).listModels("FORECAST");
        verify(dao, never()).listByTypeAndState(anyString(), any(), any());
    }

    // ── 用例 2：无匹配 ⇒ 200 + 空列表（禁 500，禁静默回退旧链造假行）──

    @Test
    @DisplayName("noMatchReturnsEmptyListNotError：无 PUBLISHED 行 ⇒ 成功信封 + 空列表（禁 500）")
    void noMatchReturnsEmptyListNotError() {
        when(dao.listByTypeAndState(isNull(), eq("PUBLISHED"), isNull()))
                .thenReturn(List.of());

        ApiResponse<List<Map<String, Object>>> resp = controller.listModels(null, "PUBLISHED", null);

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(), "空结果必须成功信封，禁 5xx");
        assertNotNull(resp.getData());
        assertTrue(resp.getData().isEmpty(), "无匹配必须空列（禁回退旧链造假行）");
    }

    @Test
    @DisplayName("combinedFiltersPushDownTogether：cognitiveModelType + registryState 组合下推（cm_ready 判定形态）")
    void combinedFiltersPushDownTogether() {
        when(dao.listByTypeAndState(isNull(), eq("PUBLISHED"), eq("CM-03")))
                .thenReturn(List.of(modelRow("r-cm", "PUBLISHED", "PASS", new BigDecimal("0.91"))));

        ApiResponse<List<Map<String, Object>>> resp = controller.listModels(null, "PUBLISHED", "CM-03");

        assertEquals(1, resp.getData().size());
        assertEquals("CM-03", resp.getData().get(0).get("cognitive_model_type"));
        verify(dao).listByTypeAndState(null, "PUBLISHED", "CM-03");
    }

    // ── 用例 3：backtestGate 键存在且透传正确（+ backtestScore nullable，禁假值）──

    @Test
    @DisplayName("backtestGateKeyPresentAndPassThrough：gate_backtest 原值透传；backtestScore 缺失为 null 禁 0")
    void backtestGateKeyPresentAndPassThrough() {
        Map<String, Object> withScore = modelRow("r1", "PUBLISHED", "PASS", new BigDecimal("0.87"));
        Map<String, Object> withoutScore = modelRow("r2", "DRAFT", "FAIL", null);
        when(dao.listModels(isNull())).thenReturn(List.of(withScore, withoutScore));

        List<Map<String, Object>> rows = service.listModels(null);

        for (Map<String, Object> row : rows) {
            assertTrue(row.containsKey("backtestGate"),
                    "backtestGate 键必存在（backtest_ready 判定面缺失 = 假真理）");
            assertTrue(row.containsKey("backtestScore"), "backtestScore 键必存在（nullable）");
        }
        assertEquals("PASS", rows.get(0).get("backtestGate"), "gate_backtest 原值透传，禁翻译/截断");
        assertEquals(new BigDecimal("0.87"), rows.get(0).get("backtestScore"),
                "NUMERIC 列必须 BigDecimal 承载（禁 double 浮点假精度）");
        assertEquals("FAIL", rows.get(1).get("backtestGate"));
        assertNull(rows.get(1).get("backtestScore"), "无分数必须 null（禁 0/硬编码假值）");
        assertEquals(rows.get(0).get("gate_backtest"), rows.get(0).get("backtestGate"),
                "新键与既有 snake_case 列名键同值（只增不改，旧键保留）");
    }

    // ── 用例 4（补强）：非契约过滤值 fail-loud 400（禁静默忽略退化为全量）──

    @ParameterizedTest(name = "{0} ⇒ 400 CORM-400")
    @ValueSource(strings = {"BOGUS", "x-published", "DRAFT2", "published-draft"})
    @DisplayName("invalidRegistryStateFailsLoud400：非 DRAFT/PUBLISHED 契约值 fail-loud，禁静默忽略")
    void invalidRegistryStateFailsLoud400(String raw) {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.listModels(null, raw, null));
        assertTrue(e.getMessage().startsWith("CORM-400"));
        verify(dao, never()).listByTypeAndState(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("invalidCognitiveModelTypeFailsLoud400：CM-08/未知字面量 ⇒ 400，禁静默忽略")
    void invalidCognitiveModelTypeFailsLoud400() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.listModels(null, null, "CM-08"));
        assertTrue(e.getMessage().startsWith("CORM-400"));
        verify(dao, never()).listByTypeAndState(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    // ── fixtures ──────────────────────────────────────────

    private static Map<String, Object> modelRow(String id, String registryState, String gateBacktest,
                                                BigDecimal backtestScore) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("model_id", "m");
        row.put("model_type", "FORECAST");
        row.put("version", 1);
        row.put("features", null);
        row.put("backtest_score", backtestScore);
        row.put("status", "active");
        row.put("cognitive_model_type", "CM-03");
        row.put("registry_state", registryState);
        row.put("content_hash", null);
        row.put("supersedes_id", null);
        row.put("gate_contract", "PASS");
        row.put("gate_regression", "PENDING");
        row.put("gate_backtest", gateBacktest);
        row.put("gate_calibration", "PASS");
        return row;
    }
}
