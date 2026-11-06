package com.chinacreator.gzcm.workspace.blueprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §6.6 完整性校验：BLOCKER 阻断，WARNING/SUGGESTION 明示但不阻断（不得静默丢弃）。 */
class ScenarioBlueprintValidatorTest {

    private final ScenarioBlueprintValidator validator = new ScenarioBlueprintValidator(
            (capabilityId, contractVersion) -> CapabilityReadService.CapabilityAvailability.unavailable("stub"),
            new ObjectMapper());

    private static ScenarioBlueprintValidator.BlueprintContent content(String gapsJson) {
        ScenarioBlueprintValidator.BlueprintContent c = new ScenarioBlueprintValidator.BlueprintContent();
        c.gapsText = gapsJson;
        c.dependenciesText = "[{\"type\":\"ontology\",\"id\":\"o-1\",\"required\":true,\"status\":\"PUBLISHED\"}]";
        c.capabilitiesText = "[]";
        c.testsText = "[{\"testId\":\"t-1\",\"priority\":\"P0\",\"status\":\"PASSED\"}]";
        return c;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> details, String key) {
        return (List<Map<String, Object>>) details.get(key);
    }

    @Test
    void openNonBlockingGapsAreReportedWithoutBlocking() {
        ScenarioBlueprintValidator.ValidationResult r = validator.validateForRelease(content(
                "[{\"gapId\":\"g-b\",\"severity\":\"BLOCKER\",\"status\":\"RESOLVED\"},"
                        + "{\"gapId\":\"g-w1\",\"severity\":\"WARNING\",\"status\":\"OPEN\",\"description\":\"口径待补\"},"
                        + "{\"gapId\":\"g-w2\",\"severity\":\"warning\",\"status\":\"RESOLVED\"},"
                        + "{\"gapId\":\"g-s1\",\"severity\":\"SUGGESTION\",\"status\":\"OPEN\"}]"));

        assertTrue(r.isPassed());
        assertTrue(r.getReasons().isEmpty());
        assertTrue(list(r.getDetails(), "openBlockerGaps").isEmpty());

        List<Map<String, Object>> warnings = list(r.getDetails(), "openWarnings");
        assertEquals(1, warnings.size());
        assertEquals("g-w1", warnings.get(0).get("gapId"));
        assertEquals("OPEN", warnings.get(0).get("status"));

        List<Map<String, Object>> suggestions = list(r.getDetails(), "openSuggestions");
        assertEquals(1, suggestions.size());
        assertEquals("g-s1", suggestions.get(0).get("gapId"));
    }

    @Test
    void closedNonBlockingStatesAreExcluded() {
        ScenarioBlueprintValidator.ValidationResult r = validator.validateForRelease(content(
                "[{\"gapId\":\"g-w\",\"severity\":\"WARNING\",\"status\":\"RESOLVED\"},"
                        + "{\"gapId\":\"g-s\",\"severity\":\"SUGGESTION\",\"status\":\"ACCEPTED_RISK\"}]"));

        assertTrue(r.isPassed());
        assertTrue(list(r.getDetails(), "openWarnings").isEmpty());
        assertTrue(list(r.getDetails(), "openSuggestions").isEmpty());
    }

    @Test
    void unstructuredGapsFailClosedWithDetailInsteadOfThrowing() {
        ScenarioBlueprintValidator.BlueprintContent c = content("缺口1: 收入口径待补");
        ScenarioBlueprintValidator.ValidationResult r = validator.validateForRelease(c);

        // GET {id}/validate 的契约是 200 + passed=false（历史蓝图 gaps 可能是自由文本）
        assertFalse(r.isPassed());
        assertTrue(r.getReasons().stream().anyMatch(s -> s.contains("fail-closed")));
        assertTrue(r.getDetails().keySet().contains("invalidContent"));
    }

    @Test
    void openBlockerFailsAndKeepsNonBlockingDetail() {
        ScenarioBlueprintValidator.ValidationResult r = validator.validateForRelease(content(
                "[{\"gapId\":\"g-b1\",\"severity\":\"BLOCKER\",\"status\":\"OPEN\"},"
                        + "{\"gapId\":\"g-w1\",\"severity\":\"WARNING\",\"status\":\"OPEN\"}]"));

        assertFalse(r.isPassed());
        assertEquals(1, list(r.getDetails(), "openBlockerGaps").size());
        assertEquals(1, list(r.getDetails(), "openWarnings").size());
        assertTrue(r.getReasons().stream().anyMatch(s -> s.contains("g-b1")));
    }
}
