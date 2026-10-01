package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.engine.security.dao.AuditDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AuditHashChainServiceTest — 审计日志 SHA-256 哈希链一致性。
 *
 * <p>Wave-5.1 T-05：模拟 4 次 append 后前缀 H1 不变、
 * 后续 Hn = SHA256(H(n-1) + fields) 的链直觉。
 * B3b-security 数据访问收口后：以内存 Map 作底层、{@link AuditDao} 作 mock 适配，
 * 避免连接 PG。</p>
 */
class AuditHashChainServiceTest {

    /** 内存表模拟 ECOS_AUDIT_LOG（已按 B3b 收口改挂 AuditDao）。 */
    private static class InMemoryAuditLog {
        final Map<Long, Map<String, Object>> rows = new ConcurrentHashMap<>();
        final AtomicInteger id = new AtomicInteger(0);

        long insertRow(String username, String operation, String entityType,
                       String entityId, String createdAt) {
            long id = this.id.incrementAndGet();
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", id);
            row.put("username", username);
            row.put("operation", operation);
            row.put("entity_type", entityType);
            row.put("entity_id", entityId);
            row.put("created_at", createdAt);
            row.put("prev_hash", null);
            row.put("curr_hash", null);
            row.put("hash_algorithm", null);
            rows.put(id, row);
            return id;
        }
    }

    private AuditHashChainService service;
    private InMemoryAuditLog store;

    static String sha256(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        this.store = new InMemoryAuditLog();
        AuditDao dao = org.mockito.Mockito.mock(AuditDao.class);

        when(dao.findAuditRowContent(anyLong())).thenAnswer(inv -> {
            long auditLogId = inv.getArgument(0);
            Map<String, Object> row = store.rows.get(auditLogId);
            if (row == null) {
                throw new org.springframework.dao.IncorrectResultSizeDataAccessException(
                        "no row for id=" + auditLogId, 0);
            }
            Map<String, Object> view = new java.util.HashMap<>();
            view.put("username", row.get("username"));
            view.put("operation", row.get("operation"));
            view.put("entity_type", row.get("entity_type"));
            view.put("entity_id", row.get("entity_id"));
            view.put("created_at", row.get("created_at"));
            return view;
        });

        when(dao.findPrevHash(anyLong(), anyString())).thenAnswer(inv -> {
            long auditLogId = inv.getArgument(0);
            String emptyHash = inv.getArgument(1);
            long prevId = auditLogId - 1;
            Map<String, Object> prev = store.rows.get(prevId);
            if (prev == null || prev.get("curr_hash") == null) {
                return emptyHash;
            }
            return (String) prev.get("curr_hash");
        });

        when(dao.updateHashFields(anyString(), anyString(), anyString(), anyLong()))
                .thenAnswer(inv -> {
                    String prev = inv.getArgument(0);
                    String curr = inv.getArgument(1);
                    String alg = inv.getArgument(2);
                    long id = inv.getArgument(3);
                    Map<String, Object> row = store.rows.get(id);
                    if (row == null) return 0;
                    row.put("prev_hash", prev);
                    row.put("curr_hash", curr);
                    row.put("hash_algorithm", alg);
                    return 1;
                });

        when(dao.findHashChainRows()).thenAnswer(inv -> {
            java.util.TreeMap<Long, Map<String, Object>> ordered =
                    new java.util.TreeMap<>(store.rows);
            java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
            for (Map<String, Object> r : ordered.values()) {
                Map<String, Object> v = new java.util.LinkedHashMap<>();
                v.put("id", r.get("id"));
                v.put("username", r.get("username"));
                v.put("operation", r.get("operation"));
                v.put("entity_type", r.get("entity_type"));
                v.put("entity_id", r.get("entity_id"));
                v.put("created_at", r.get("created_at"));
                v.put("prev_hash", r.get("prev_hash"));
                v.put("curr_hash", r.get("curr_hash"));
                out.add(v);
            }
            return out;
        });

        this.service = new AuditHashChainService(dao);
    }

    @Test
    @DisplayName("4 次 append 后哈希链一贯：前缀 H1 固定，后续可递归重算")
    void hashChainStableAfterFourAppends() {
        // 模拟 append 1..4
        store.insertRow("alice", "LOGIN", "USER", "u1", "2026-09-02T10:00:00");
        service.stampHashChain(1L);

        store.insertRow("bob", "CREATE", "DOC", "d1", "2026-09-02T10:01:00");
        service.stampHashChain(2L);

        store.insertRow("alice", "UPDATE", "DOC", "d1", "2026-09-02T10:02:00");
        service.stampHashChain(3L);

        store.insertRow("charlie", "DELETE", "USER", "u2", "2026-09-02T10:03:00");
        service.stampHashChain(4L);

        // 验证：首条 prevHash = EMPTY_HASH（SHA-256("")）
        Map<String, Object> row1 = store.rows.get(1L);
        assertEquals(sha256(""), row1.get("prev_hash"));

        // 第二条 prevHash 应等于第一条 currHash
        Map<String, Object> row2 = store.rows.get(2L);
        assertEquals(row1.get("curr_hash"), row2.get("prev_hash"));

        // 第三条 prevHash 应等于第二条 currHash
        Map<String, Object> row3 = store.rows.get(3L);
        assertEquals(row2.get("curr_hash"), row3.get("prev_hash"));

        // 第四条 prevHash 应等于第三条 currHash
        Map<String, Object> row4 = store.rows.get(4L);
        assertEquals(row3.get("curr_hash"), row4.get("prev_hash"));

        // 每条 currHash = SHA256(prevHash|username|operation|entity_type|entity_id|created_at)
        for (long i = 1; i <= 4; i++) {
            Map<String, Object> row = store.rows.get(i);
            String prevHash = (String) row.get("prev_hash");
            String expected = sha256(prevHash
                    + "|" + row.get("username")
                    + "|" + row.get("operation")
                    + "|" + row.get("entity_type")
                    + "|" + row.get("entity_id")
                    + "|" + row.get("created_at"));
            assertEquals(expected, row.get("curr_hash"), "row " + i + " hash mismatch");
        }
    }

    @Test
    @DisplayName("verifyHashChain — 4 条完整记录应 valid=true 且 totalChecked=4")
    void verifyHashChainPassesWhenIntact() {
        for (int i = 1; i <= 4; i++) {
            store.insertRow("u" + i, "OP" + i, "TYPE" + i, "id" + i, "2026-09-02T10:0" + (i - 1) + ":00");
            service.stampHashChain((long) i);
        }
        Map<String, Object> result = service.verifyHashChain();
        assertTrue((Boolean) result.get("valid"));
        assertEquals(4, ((Number) result.get("totalChecked")).intValue());
        assertEquals("SHA-256", result.get("algorithm"));
        assertEquals(null, result.get("brokenAt"));
    }

    @Test
    @DisplayName("verifyHashChain — 篡改一条记录后应 valid=false 且 brokenAt 指明位置")
    void verifyHashChainDetectsTampering() {
        for (int i = 1; i <= 4; i++) {
            store.insertRow("u" + i, "OP" + i, "TYPE" + i, "id" + i, "2026-09-02T10:0" + (i - 1) + ":00");
            service.stampHashChain((long) i);
        }
        // 篡改第 2 条 username → 链断裂
        store.rows.get(2L).put("username", "EVIL");

        Map<String, Object> result = service.verifyHashChain();
        assertFalse((Boolean) result.get("valid"));
        assertEquals(2L, ((Number) result.get("brokenAt")).longValue());
    }
}
