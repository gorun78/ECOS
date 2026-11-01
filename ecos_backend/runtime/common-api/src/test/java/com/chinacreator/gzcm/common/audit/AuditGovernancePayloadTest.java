package com.chinacreator.gzcm.common.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 审计载荷白名单提取（#147M）：只认契约列、集合转逗号串、timestamp 不进补写列。 */
class AuditGovernancePayloadTest {

    @Test
    void extractsOnlyContractColumns() {
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("id", "123");
        evt.put("audit_event_id", "evt-1");
        evt.put("change_request_id", "cr-1");
        evt.put("risk_level", "L3");
        evt.put("approver_ids", List.of("a", "b"));
        evt.put("timestamp", "2026-11-01T00:00:00Z");
        evt.put("unknown_key", "x");

        Map<String, Object> cols = AuditGovernancePayload.extract(evt);
        assertEquals("evt-1", cols.get("audit_event_id"));
        assertEquals("cr-1", cols.get("change_request_id"));
        assertEquals("L3", cols.get("risk_level"));
        assertEquals("a,b", cols.get("approver_ids"));
        assertFalse(cols.containsKey("timestamp"));
        assertFalse(cols.containsKey("unknown_key"));
        assertFalse(cols.containsKey("id"));
        assertTrue(AuditGovernancePayload.hasGovernanceKeys(evt));
    }

    @Test
    void emptyOrPlainEventsYieldNothing() {
        assertTrue(AuditGovernancePayload.extract(null).isEmpty());
        assertFalse(AuditGovernancePayload.hasGovernanceKeys(Map.of("action", "login")));
    }
}
