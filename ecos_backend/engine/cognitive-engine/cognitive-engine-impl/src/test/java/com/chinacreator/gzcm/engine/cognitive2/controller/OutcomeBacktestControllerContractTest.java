package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 新端点契约与"禁前端传阈值"冻结测试（详设-09 v1.2 §十一 H-4 要点 1/3，R-66 ①）。
 *
 * <p>两面各锁一组：</p>
 * <ol>
 *   <li><b>契约单源</b>：两个新路径由 {@link CognitiveApiPaths} 常量拼装，Controller 源内零
 *       {@code /api} 字面量，绝对形态 = 前缀 + 相对（API 只增不改，旧端点签名零触碰）；</li>
 *   <li><b>阈值不采信</b>：任何阈值形态的查询参数键名 / 请求体键名（含嵌套与数组元素）一律 400，
 *       普通参数正常放行 —— 结构上保证阈值只可能来自 sysman 配置门面。</li>
 * </ol>
 */
@DisplayName("H-4 回测端点契约 + 前端阈值拒绝（OutcomeBacktestController）")
class OutcomeBacktestControllerContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path CTRL_SRC = Path.of("src", "main", "java", "com", "chinacreator", "gzcm",
            "engine", "cognitive2", "controller", "OutcomeBacktestController.java");

    @Test
    @DisplayName("绝对路径 = 前缀 + 相对，且只增子路径不动既有端点")
    void pathsAreSingleSourced() {
        assertEquals("/api/v1/cognitive/outcomes/{evaluationId}/backtest", CognitiveApiPaths.OUTCOMES_BACKTEST);
        assertEquals("/api/v1/cognitive/outcomes/{evaluationId}/review-tasks",
                CognitiveApiPaths.OUTCOMES_REVIEW_TASKS);
        assertEquals(CognitiveApiPaths.COGNITIVE + CognitiveApiPaths.OUTCOMES_BACKTEST_REL,
                CognitiveApiPaths.OUTCOMES_BACKTEST);
        assertEquals(CognitiveApiPaths.COGNITIVE + CognitiveApiPaths.OUTCOMES_REVIEW_TASKS_REL,
                CognitiveApiPaths.OUTCOMES_REVIEW_TASKS);
    }

    @Test
    @DisplayName("映射注解不写路径字面量（路径只经 CognitiveApiPaths 常量）")
    void controllerMappingsUseContractConstantsOnly() throws IOException {
        String text = Files.readString(CTRL_SRC, StandardCharsets.UTF_8);
        for (String annotation : List.of("@RequestMapping(\"", "@GetMapping(\"", "@PostMapping(\"",
                "@PutMapping(\"", "@DeleteMapping(\"", "@PatchMapping(\"")) {
            assertFalse(text.contains(annotation), annotation + " 直接写了路径字面量");
        }
        assertTrue(text.contains("@RequestMapping(CognitiveApiPaths.COGNITIVE)"));
        assertTrue(text.contains("CognitiveApiPaths.OUTCOMES_BACKTEST_REL"));
        assertTrue(text.contains("CognitiveApiPaths.OUTCOMES_REVIEW_TASKS_REL"));
    }

    @Test
    @DisplayName("查询参数带阈值形态键名 ⇒ 400 且逐枚点出，绝不静默忽略")
    void queryParamThresholdKeysAreRejected() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("evaluationId", "eval-001");
        params.put("mapeThreshold", "0.99");
        params.put("min_samples", "1");
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> OutcomeBacktestController.FrontendThresholdGuard.rejectQueryParams(params));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        assertTrue(e.missing.contains("FRONTEND_THRESHOLD_REJECTED (R-66 ①)"));
        assertTrue(e.missing.contains("query.mapeThreshold") && e.missing.contains("query.min_samples"));
        assertFalse(e.missing.contains("query.evaluationId"), "普通参数不得被误伤");
    }

    @Test
    @DisplayName("普通查询参数与空参数 ⇒ 放行（拒绝面只覆盖阈值形态）")
    void benignQueryParamsPass() {
        OutcomeBacktestController.FrontendThresholdGuard.rejectQueryParams(
                Map.of("evaluationId", "eval-001", "windowFrom", "2026-01-01"));
        OutcomeBacktestController.FrontendThresholdGuard.rejectQueryParams(null);
        OutcomeBacktestController.FrontendThresholdGuard.rejectQueryParams(Map.of());
    }

    @Test
    @DisplayName("请求体嵌套与数组元素里的阈值键名同样拒绝")
    void nestedBodyKeysAreRejected() throws IOException {
        JsonNode body = MAPPER.readTree("{\"scope\":{\"runId\":\"r-1\",\"band\":0.02},"
                + "\"gates\":[{\"coverage\":0.5}],\"note\":\"ok\"}");
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> OutcomeBacktestController.FrontendThresholdGuard.rejectBodyKeys(body));
        assertTrue(e.missing.contains("scope.band"));
        assertTrue(e.missing.contains("gates[0].coverage"));
        assertFalse(e.missing.contains("note"));
    }

    @Test
    @DisplayName("空体 / null 体 / 无阈值键的体 ⇒ 放行（既有请求面零改动）")
    void bodyWithoutThresholdKeysPasses() throws IOException {
        OutcomeBacktestController.FrontendThresholdGuard.rejectBodyKeys(null);
        OutcomeBacktestController.FrontendThresholdGuard.rejectBodyKeys(MAPPER.readTree("null"));
        OutcomeBacktestController.FrontendThresholdGuard.rejectBodyKeys(
                MAPPER.readTree("{\"runId\":\"r-1\",\"metricRefs\":[\"gm.revenue\"]}"));
    }
}
