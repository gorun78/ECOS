package com.chinacreator.gzcm.engine.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.security.dao.AuditDao;

/**
 * 审计消费者治理列补写（§2.9.4③；#147M）。
 *
 * <p>钉两件事：① 事件带治理键时，基础行落库后补写列（白名单、不含 timestamp）；
 * ② 无治理键时不发无谓 UPDATE。</p>
 */
class AuditConsumerGovernanceFillTest {

    /** 记录落库/补写的 DAO 替身。 */
    private static final class RecordingDao extends AuditDao {
        int inserts;
        Map<String, Object> governanceCols;
        String governanceId;

        @Override
        public int insertAuditLogIdempotent(String id, String userId, String username, String action,
                                            String resource, String result, String detail, String ip) {
            inserts++;
            return 1;
        }

        @Override
        public int fillGovernanceColumns(String id, Map<String, Object> cols) {
            this.governanceId = id;
            this.governanceCols = cols;
            return 1;
        }
    }

    private static AuditKafkaConsumer consumer(RecordingDao dao) {
        return new AuditKafkaConsumer(dao, null);
    }

    @Test
    void governanceKeysAreFilledAfterInsert() {
        RecordingDao dao = new RecordingDao();
        Map<String, Object> evt = new HashMap<>();
        evt.put("id", "1767225600000123");
        evt.put("userId", "u-1");
        evt.put("action", "ontology.publish");
        evt.put("audit_event_id", "evt-1");
        evt.put("change_request_id", "cr-1");
        evt.put("risk_level", "L3");
        evt.put("approver_ids", java.util.List.of("u-2", "u-3"));
        evt.put("non_column_key", "ignored");
        evt.put("timestamp", "2026-11-01T00:00:00Z");

        consumer(dao).onAuditEvent(evt);

        assertEquals(1, dao.inserts);
        assertEquals("1767225600000123", dao.governanceId);
        assertEquals("evt-1", dao.governanceCols.get("audit_event_id"));
        assertEquals("cr-1", dao.governanceCols.get("change_request_id"));
        assertEquals("L3", dao.governanceCols.get("risk_level"));
        assertEquals("u-2,u-3", dao.governanceCols.get("approver_ids"));
        assertTrue(!dao.governanceCols.containsKey("timestamp"), "timestamp 落 created_at，不走补写列");
        assertTrue(!dao.governanceCols.containsKey("non_column_key"), "非列键必须丢弃");
    }

    @Test
    void plainEventDoesNotTriggerGovernanceUpdate() {
        RecordingDao dao = new RecordingDao();
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("id", "1767225600000999");
        evt.put("action", "login");
        evt.put("result", "OK");

        consumer(dao).onAuditEvent(evt);

        assertEquals(1, dao.inserts);
        assertEquals(null, dao.governanceCols);
    }
}
