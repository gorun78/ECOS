package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import com.chinacreator.gzcm.engine.cognitive.api.framework.CognitiveFrameworkDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class FrameworkSelectionServiceTest {

    private CognitiveFrameworkRegistryService registry;
    private FrameworkSelectionService service;

    @BeforeEach
    void setUp() {
        registry = Mockito.mock(CognitiveFrameworkRegistryService.class);
        service = new FrameworkSelectionService(registry, new PlanValidator());
    }

    @Test
    void hardConstraintExcludesEvenWhenCandidateWouldScoreHigher() {
        CognitiveFrameworkDefinition good = definition("CF-01-01", "1.0.0",
                List.of("problem_framing"), List.of(), List.of());
        CognitiveFrameworkDefinition missingInput = definition("CF-03-03", "1.0.0",
                List.of("problem_framing"), List.of("anomaly_definition_present",
                        "comparison_baseline_present"), List.of());
        when(registry.list(null, null, "PUBLISHED")).thenReturn(List.of(good, missingInput));
        when(registry.get(anyString(), anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return "CF-01-01".equals(id) ? good : missingInput;
        });

        Map<String, Object> result = service.select(Map.of(
                "problemType", "problem_framing",
                "domain", "enterprise_operations",
                "presentInputs", List.of(),
                "availableCapabilities", List.of(),
                "budget", Map.of("maxSteps", 10)));

        assertEquals("PLANNED", result.get("status"));
        List<Map<String, Object>> selected = castList(result.get("selectedFrameworks"));
        assertEquals(1, selected.size());
        assertEquals("CF-01-01", selected.get(0).get("frameworkId"));
        assertEquals(false, selected.get(0).get("calibrated"));
        assertEquals("ORDINAL_ONLY", selected.get(0).get("scoreKind"));
        assertEquals("HARD_FILTERS_PASSED", selected.get(0).get("selectionReason"));

        List<Map<String, Object>> used = castList(result.get("usedFrameworkVersions"));
        assertEquals(1, used.size());
        assertEquals("CF-01-01", used.get(0).get("frameworkId"));
        assertEquals("1.0.0", used.get(0).get("version"));
        assertEquals("HARD_FILTERS_PASSED", used.get(0).get("selectionReason"));

        List<Map<String, Object>> excluded = castList(result.get("excludedFrameworks"));
        assertEquals(1, excluded.size());
        assertEquals("CF-03-03", excluded.get(0).get("frameworkId"));
        assertTrue(String.valueOf(excluded.get(0).get("reasons")).contains(
                FrameworkSelectionService.EXCLUDED_MISSING_INPUT));
        assertTrue(castList(result.get("missingInputs")).contains("anomaly_definition_present"));
    }

    @Test
    void unauthorizedSelectsNothingAndRequestsHuman() {
        CognitiveFrameworkDefinition f = definition("CF-04-01", "1.0.0",
                List.of("evidence_review"), List.of(), List.of());
        when(registry.list(null, null, "PUBLISHED")).thenReturn(List.of(f));

        Map<String, Object> result = service.select(Map.of("authorized", false));
        assertEquals("NEEDS_INPUT_OR_HUMAN", result.get("status"));
        assertTrue(((List<?>) result.get("selectedFrameworks")).isEmpty());
        assertTrue(String.valueOf(castList(result.get("excludedFrameworks")).get(0).get("reasons"))
                .contains(FrameworkSelectionService.EXCLUDED_PERMISSION));
    }

    @Test
    void budgetMaxStepsExcludesCandidate() {
        CognitiveFrameworkDefinition f = definition("CF-06-02", "1.0.0",
                List.of("scenario_planning"), List.of(), List.of());
        when(registry.list(null, null, "PUBLISHED")).thenReturn(List.of(f));

        Map<String, Object> result = service.select(Map.of(
                "problemType", "scenario_planning",
                "budget", Map.of("maxSteps", 1)));
        assertEquals("NEEDS_INPUT_OR_HUMAN", result.get("status"));
        assertTrue(String.valueOf(castList(result.get("excludedFrameworks")).get(0).get("reasons"))
                .contains(FrameworkSelectionService.EXCLUDED_BUDGET));
    }

    @Test
    void selectedPlanCarriesDagAndValidation() {
        CognitiveFrameworkDefinition f = definition("CF-01-01", "1.0.0",
                List.of("problem_framing"), List.of(), List.of());
        when(registry.list(null, null, "PUBLISHED")).thenReturn(List.of(f));
        when(registry.get(anyString(), anyString())).thenReturn(f);

        Map<String, Object> result = service.select(Map.of(
                "problemType", "problem_framing",
                "budget", Map.of("maxSteps", 5, "maxTokens", 12000),
                "availableCapabilities", List.of()));

        assertEquals("PLANNED", result.get("status"));
        Map<String, Object> plan = castMap(result.get("planDraft"));
        assertEquals(1, ((List<?>) plan.get("steps")).size());
        Map<String, Object> validation = castMap(result.get("planValidation"));
        assertEquals(true, validation.get("passed"));
        assertEquals(true, validation.get("dagAcyclic"));
        assertFalse(result.containsKey("planRevision"));
        assertFalse(((Map<?, ?>) result.get("selectionTrace")).containsKey("probabilitySemantics")
                && "PROBABILITY".equals(((Map<?, ?>) result.get("selectionTrace")).get("probabilitySemantics")));
    }

    private static CognitiveFrameworkDefinition definition(String id, String version, List<String> problemTypes,
                                                           List<String> preconditions,
                                                           List<String> capabilities) {
        CognitiveFrameworkDefinition d = new CognitiveFrameworkDefinition();
        d.setFrameworkId(id);
        d.setVersion(version);
        d.setName(id);
        d.setCategory("TEST");
        d.setStatus("PUBLISHED");
        d.setProblemTypes(problemTypes);
        d.setDomains(List.of("enterprise_operations"));
        d.setPreconditions(preconditions);
        d.setRequiredCapabilities(capabilities);
        d.setInputSchemaRef("ecos://in/" + id);
        d.setOutputSchemaRef("ecos://out/" + id);
        d.setContractResolvable(true);
        d.setSteps(List.of(Map.of("stepId", "s1"),
                Map.of("stepId", "s2"), Map.of("stepId", "s3")));
        d.setStopConditions(List.of("max_iterations"));
        d.setGovernance(new LinkedHashMap<>(Map.of("productionApprovalRequired", false)));
        return d;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object raw) {
        return (List<Map<String, Object>>) raw;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object raw) {
        return (Map<String, Object>) raw;
    }
}
