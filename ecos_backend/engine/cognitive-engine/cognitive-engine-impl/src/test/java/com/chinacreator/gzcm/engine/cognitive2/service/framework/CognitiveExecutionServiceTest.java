package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitiveExecutionServiceTest {

    private final CognitiveExecutionService service = new CognitiveExecutionService();

    @Test
    void idempotentCreateReturnsSameRecordWithoutSecondSideEffect() {
        Map<String, Object> first = service.create(Map.of(
                "idempotencyKey", "k-1", "scenarioId", "s-1", "runMode", "SANDBOX"));
        Map<String, Object> second = service.create(Map.of(
                "idempotencyKey", "k-1", "scenarioId", "s-1", "runMode", "SANDBOX"));
        assertEquals(first.get("executionId"), second.get("executionId"));
        assertEquals(true, second.get("idempotentReplay"));
    }

    @Test
    void successFlowRequiresVerificationGate() {
        String id = (String) service.create(Map.of("scenarioId", "s-1")).get("executionId");
        service.transition(id, Map.of("targetStatus", "PLANNED"));
        service.transition(id, Map.of("targetStatus", "VALIDATING"));
        service.transition(id, Map.of("targetStatus", "RUNNING"));
        service.transition(id, Map.of("targetStatus", "VERIFYING"));

        CognitiveGuardException denied = assertThrows(CognitiveGuardException.class,
                () -> service.transition(id, Map.of("targetStatus", "SUCCEEDED",
                        "schemaValid", true, "businessValid", false)));
        assertEquals(CognitiveErrorCode.E422_GATES_NOT_PASSED, denied.errorCode);

        Map<String, Object> done = service.transition(id, Map.of(
                "targetStatus", "SUCCEEDED", "schemaValid", true, "businessValid", true,
                "businessConclusionStatus", "UNVERIFIED"));
        assertEquals("SUCCEEDED", done.get("status"));
        assertEquals("UNVERIFIED", done.get("businessConclusionStatus"));
    }

    @Test
    void illegalAndTerminalTransitionsAreRejected() {
        String id = (String) service.create(Map.of()).get("executionId");
        CognitiveGuardException jump = assertThrows(CognitiveGuardException.class,
                () -> service.transition(id, Map.of("targetStatus", "RUNNING")));
        assertEquals(CognitiveErrorCode.E409_STATE_ILLEGAL, jump.errorCode);

        service.transition(id, Map.of("targetStatus", "CANCELLED"));
        CognitiveGuardException afterTerminal = assertThrows(CognitiveGuardException.class,
                () -> service.transition(id, Map.of("targetStatus", "PLANNED")));
        assertEquals(CognitiveErrorCode.E409_STATE_ILLEGAL, afterTerminal.errorCode);
    }

    @Test
    void recordKeepsSourceAssetVersionsForTraceability() {
        Map<String, Object> record = service.create(Map.of(
                "scenarioId", "s-1",
                "contextDigest", "sha256:abc",
                "contextSources", List.of(
                        Map.of("sourceType", "DATA_ASSET", "sourceId", "a-1", "version", "7"),
                        Map.of("sourceType", "ONTOLOGY", "sourceId", "e-9", "version", "2026-10-01T00:00:00Z"))));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) record.get("contextSources");
        assertEquals(2, sources.size());
        assertEquals("DATA_ASSET", sources.get(0).get("sourceType"));
        assertEquals("7", sources.get(0).get("version"));
        assertEquals("2026-10-01T00:00:00Z", sources.get(1).get("version"));
    }

    @Test
    void sourceWithoutVersionIsRejected() {
        CognitiveGuardException denied = assertThrows(CognitiveGuardException.class,
                () -> service.create(Map.of(
                        "scenarioId", "s-1",
                        "contextSources", List.of(Map.of("sourceType", "DATA_ASSET", "sourceId", "a-1")))));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, denied.errorCode);
    }

    @Test
    void recordCarriesTraceabilityKeysAndFormalDefault() {
        Map<String, Object> record = service.create(Map.of(
                "scenarioId", "s-1",
                "blueprintId", "bp-1",
                "blueprintVersion", 4,
                "contextDigest", "sha256:abc",
                "frameworkRefs", List.of(Map.of("frameworkId", "CF-01-01", "version", "1.0.0"))));
        assertEquals("FORMAL", record.get("runMode"));
        assertEquals("bp-1", record.get("blueprintId"));
        assertEquals("sha256:abc", record.get("contextDigest"));
        assertEquals("NOT_EVALUATED", record.get("businessConclusionStatus"));
        assertFalse(((List<?>) record.get("history")).isEmpty());
        assertTrue(record.containsKey("executionId"));
    }
}
