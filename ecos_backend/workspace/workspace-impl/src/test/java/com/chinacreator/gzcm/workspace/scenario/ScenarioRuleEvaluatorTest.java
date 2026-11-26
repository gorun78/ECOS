package com.chinacreator.gzcm.workspace.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/** 场景规则求值门禁：只读拉取、受限 SpEL、单条失败不扩散。 */
class ScenarioRuleEvaluatorTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ScenarioRuleEvaluator evaluator(RestTemplate rest) {
        when(rest.exchange(eq("/ecos/rules"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("code", 0, "data", List.of(
                        Map.of("id", "r1", "name", "亏损预警", "expression", "loss > 100", "action", "ALERT"),
                        Map.of("id", "r2", "name", "受限表达式", "expression", "T(java.lang.Runtime).getRuntime()", "action", "X")
                ))));
        return new ScenarioRuleEvaluator(rest);
    }

    @Test
    @DisplayName("命中/未命中/缺失/受限表达式分别给出真实结论，不阻断其它规则")
    void evaluatesWithPerRuleIsolation() {
        ScenarioRuleEvaluator eval = evaluator(mock(RestTemplate.class));
        List<Map<String, Object>> rows = eval.evaluate(List.of("r1", "r2", "missing"),
                Map.of("loss", 200));

        assertEquals(3, rows.size());
        assertEquals(true, rows.get(0).get("matched"));
        assertEquals("ALERT", rows.get(0).get("action"));

        assertEquals(false, rows.get(1).get("matched"));
        assertNotNull(rows.get(1).get("error"), "受限表达式必须被拒并说明原因");

        assertEquals(false, rows.get(2).get("matched"));
        assertTrue(String.valueOf(rows.get(2).get("error")).contains("规则不存在"));
    }

    @Test
    @DisplayName("未给事实时表达式求值失败如实记 error，不抛穿")
    void missingFactFailsPerRule() {
        ScenarioRuleEvaluator eval = evaluator(mock(RestTemplate.class));
        List<Map<String, Object>> rows = eval.evaluate(List.of("r1"), Map.of());
        assertEquals(false, rows.get(0).get("matched"));
        assertNotNull(rows.get(0).get("error"));
    }
}
