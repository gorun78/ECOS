package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanValidatorTest {

    private final PlanValidator validator = new PlanValidator();

    @Test
    void validPlanPasses() {
        Map<String, Object> result = validator.validate(Map.of(
                "steps", List.of(step("s1", List.of(), "in/1", "out/1", List.of("KB_RETRIEVE")),
                        step("s2", List.of("s1"), "in/2", "out/2", List.of())),
                "availableCapabilities", List.of("KB_RETRIEVE"),
                "budget", Map.of("maxSteps", 5, "maxTokens", 5000)));
        assertTrue((Boolean) result.get("passed"));
        assertTrue((Boolean) result.get("dagAcyclic"));
    }

    @Test
    void cycleIsRejected() {
        Map<String, Object> result = validator.validate(Map.of(
                "steps", List.of(step("s1", List.of("s2"), "in/1", "out/1", List.of()),
                        step("s2", List.of("s1"), "in/2", "out/2", List.of()))));
        assertFalse((Boolean) result.get("passed"));
        assertFalse((Boolean) result.get("dagAcyclic"));
        assertTrue(codes(result).contains(PlanValidator.ISSUE_CYCLE));
    }

    @Test
    void missingSchemaCapabilityBudgetAndSideEffectAreRejected() {
        Map<String, Object> step = step("s1", List.of(), "", "out/1", List.of("MISSING_CAP"));
        step.put("allowSideEffects", true);
        Map<String, Object> result = validator.validate(Map.of(
                "steps", List.of(step),
                "availableCapabilities", List.of("KB_RETRIEVE"),
                "budget", Map.of("maxSteps", 1, "maxTokens", 10)));
        assertFalse((Boolean) result.get("passed"));
        List<String> codes = codes(result);
        assertTrue(codes.contains(PlanValidator.ISSUE_MISSING_SCHEMA));
        assertTrue(codes.contains(PlanValidator.ISSUE_MISSING_CAPABILITY));
        assertTrue(codes.contains(PlanValidator.ISSUE_BUDGET));
        assertTrue(codes.contains(PlanValidator.ISSUE_SIDE_EFFECT));
    }

    @Test
    void unknownDependencyAndPermissionAreRejected() {
        Map<String, Object> result = validator.validate(Map.of(
                "steps", List.of(step("s1", List.of("ghost"), "in/1", "out/1", List.of())),
                "authorized", false));
        assertFalse((Boolean) result.get("passed"));
        List<String> codes = codes(result);
        assertTrue(codes.contains(PlanValidator.ISSUE_UNKNOWN_DEPENDENCY));
        assertTrue(codes.contains(PlanValidator.ISSUE_PERMISSION));
    }

    @Test
    void emptyPlanIsRejected() {
        Map<String, Object> result = validator.validate(Map.of());
        assertEquals(false, result.get("passed"));
    }

    private static Map<String, Object> step(String id, List<String> dependsOn, String in, String out,
                                            List<String> caps) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("stepId", id);
        s.put("dependsOn", dependsOn);
        s.put("inputSchemaRef", in);
        s.put("outputSchemaRef", out);
        s.put("requiredCapabilities", caps);
        s.put("estimatedTokens", 50);
        return s;
    }

    @SuppressWarnings("unchecked")
    private static List<String> codes(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("issues")).stream()
                .map(i -> String.valueOf(i.get("code"))).toList();
    }
}
