package com.chinacreator.gzcm.engine.ontology.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.audit.AuditEventContract;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

/**
 * 治理审计事件投递单源（#147M）：{@code id} 必须可落 bigint 主键（否则 consumer 以「缺 id」丢弃），
 * 治理列必须随事件带出；总线未装配时返回 false（禁伪成功）。
 */
class SecurityEngineClientAuditContractTest {

    private static final class CapturingBus implements EventBusService {
        String topic;
        Map<String, Object> payload;

        @Override
        public void publish(String topic, Object payload) {
            this.topic = topic;
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) payload;
            this.payload = cast;
        }

        @Override
        public void subscribe(String topic, Class<?> payloadType, java.util.function.Consumer<Object> handler) {
        }

        @Override
        public boolean isKafkaAvailable() {
            return false;
        }
    }

    private static SecurityEngineClient clientWithBus(EventBusService bus) throws Exception {
        SecurityEngineClient client = new SecurityEngineClient("http://localhost:18081", 100);
        Field f = SecurityEngineClient.class.getDeclaredField("eventBus");
        f.setAccessible(true);
        f.set(client, bus);
        return client;
    }

    private static AuditEventContract contract() {
        return new AuditEventContract("evt-1", "cr-1", "u-1", List.of("u-2", "u-3"),
                "ontology", "rule", "r-1", "L3", "ontology-model", "abc123", "parent00",
                "sha256:aa", "enterprise", "policy-v3", "dep-1", "sha256:before", "sha256:after",
                "APPLIED", "VERIFYING", Instant.parse("2026-11-01T00:00:00Z"), "trace-1");
    }

    @Test
    void governanceEventCarriesNumericIdAndContractColumns() throws Exception {
        CapturingBus bus = new CapturingBus();
        SecurityEngineClient client = clientWithBus(bus);

        assertTrue(client.auditGovernance("ontology.publish", contract()));

        assertEquals("ecos.audit", bus.topic);
        Map<String, Object> payload = bus.payload;
        String id = String.valueOf(payload.get("id"));
        assertTrue(id.matches("\\d{10,}"), "id 必须是数字串才能落 bigint 主键: " + id);
        assertEquals("evt-1", payload.get("audit_event_id"));
        assertEquals("cr-1", payload.get("change_request_id"));
        assertEquals("u-2,u-3", payload.get("approver_ids"));
        assertEquals("L3", payload.get("risk_level"));
        assertEquals("abc123", payload.get("git_commit_sha"));
        assertEquals("trace-1", payload.get("trace_id"));
        assertEquals("ontology", payload.get("domain"));
        assertEquals("ontology", payload.get("resource"));
        // 基础键面保持既有契约（不破坏既有 consumer 腿）
        assertTrue(payload.containsKey("eventId"));
        assertTrue(payload.containsKey("timestamp"));
        assertTrue(payload.containsKey("userId"));
    }

    @Test
    void noBusMeansFalseNotFakeSuccess() throws Exception {
        SecurityEngineClient client = clientWithBus(null);
        assertFalse(client.auditGovernance("ontology.publish", contract()));
        assertFalse(client.audit("x", "OK"));
    }

    @Test
    void plainAuditAlsoCarriesNumericId() throws Exception {
        CapturingBus bus = new CapturingBus();
        SecurityEngineClient client = clientWithBus(bus);
        assertTrue(client.audit("ontology.read", Map.of("k", "v")));
        String id = String.valueOf(bus.payload.get("id"));
        assertTrue(id.matches("\\d{10,}"));
        Map<String, Object> expected = new LinkedHashMap<>(Map.of("k", "v"));
        assertEquals(expected.toString(), String.valueOf(bus.payload.get("result")));
    }
}
