package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import com.chinacreator.gzcm.engine.cognitive.api.framework.CognitiveFrameworkDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 服务级场景→认知链路（详细设计-场景工作台 §6；CE-04/§7）。
 * 不冒充 live E2E：仅证明同一进程内 Context/选择/计划/执行记录契约可贯通并保留关联键。
 */
class CognitiveScenarioIntegrationTest {

    @Test
    @DisplayName("场景请求 → 硬过滤选择 → 计划校验 → 执行记录 → 验证通过（业务结论独立）")
    void scenarioToCognitiveExecutionFlow() {
        CognitiveFrameworkRegistryService registry = mock(CognitiveFrameworkRegistryService.class);
        CognitiveFrameworkDefinition f = new CognitiveFrameworkDefinition();
        f.setFrameworkId("CF-03-03");
        f.setVersion("1.0.0");
        f.setName("abduction");
        f.setCategory("ABDUCTIVE_REASONING");
        f.setStatus("PUBLISHED");
        f.setProblemTypes(List.of("metric_deviation"));
        f.setDomains(List.of("enterprise_operations"));
        f.setPreconditions(List.of("anomaly_definition_present"));
        f.setInputSchemaRef("ecos://in");
        f.setOutputSchemaRef("ecos://out");
        f.setContractResolvable(true);
        f.setSteps(List.of(Map.of("stepId", "s1"), Map.of("stepId", "s2")));
        f.setGovernance(Map.of("productionApprovalRequired", false));
        when(registry.list(null, null, "PUBLISHED")).thenReturn(List.of(f));
        when(registry.get("CF-03-03", "1.0.0")).thenReturn(f);

        FrameworkSelectionService selection = new FrameworkSelectionService(registry, new PlanValidator());
        Map<String, Object> selected = selection.select(Map.of(
                "scenarioId", "scenario-annual-forecast",
                "requestId", "req-1",
                "contextDigest", "sha256:abc",
                "blueprintVersion", 4,
                "problemType", "metric_deviation",
                "domain", "enterprise_operations",
                "presentInputs", List.of("anomaly_definition_present"),
                "availableCapabilities", List.of(),
                "budget", Map.of("maxSteps", 10, "maxTokens", 12000)));

        assertEquals("PLANNED", selected.get("status"));
        Map<String, Object> plan = castMap(selected.get("planDraft"));
        assertEquals("sha256:abc", plan.get("contextDigest"));
        assertEquals(4, plan.get("blueprintVersion"));
        Map<String, Object> validation = castMap(selected.get("planValidation"));
        assertEquals(true, validation.get("passed"));

        CognitiveExecutionService executions = new CognitiveExecutionService();
        String executionId = (String) executions.create(Map.of(
                "scenarioId", "scenario-annual-forecast",
                "blueprintId", "bp-1",
                "blueprintVersion", 4,
                "contextDigest", "sha256:abc",
                "contextSources", List.of(
                        Map.of("sourceType", "DATA_ASSET", "sourceId", "asset-revenue", "version", "12"),
                        Map.of("sourceType", "ONTOLOGY", "sourceId", "entity-contract", "version", "3")),
                "planId", plan.get("planId"),
                "frameworkRefs", List.of(Map.of("frameworkId", "CF-03-03", "version", "1.0.0")),
                "idempotencyKey", "scenario-flow-1")).get("executionId");

        for (String s : List.of("PLANNED", "VALIDATING", "RUNNING", "VERIFYING")) {
            executions.transition(executionId, Map.of("targetStatus", s));
        }
        Map<String, Object> done = executions.transition(executionId, Map.of(
                "targetStatus", "SUCCEEDED",
                "schemaValid", true,
                "businessValid", true,
                "businessConclusionStatus", "UNVERIFIED"));

        assertEquals("SUCCEEDED", done.get("status"));
        assertEquals("UNVERIFIED", done.get("businessConclusionStatus"));
        assertEquals("sha256:abc", done.get("contextDigest"));
        assertEquals(4, done.get("blueprintVersion"));
        assertTrue(done.get("frameworkRefs") instanceof List);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) done.get("contextSources");
        assertEquals(2, sources.size());
        assertEquals("12", sources.get(0).get("version"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object raw) {
        return (Map<String, Object>) raw;
    }
}
