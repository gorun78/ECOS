package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AuditHashChainServiceTest — 审计日志 SHA-256 哈希链一致性。
 *
 * <p>Wave-5.1 T-05：模拟 4 次 append 后前缀 H1 不变、
 * 后续 Hn = SHA256(H(n-1) + fields) 的链直觉。
 * 使用内存 JdbcTemplate 子类避免连接 PG。
 */
class AuditHashChainServiceTest {

    /**
     * 内存表模拟 ECOS_AUDIT_LOG。
     */
    private static class InMemoryJdbc extends JdbcTemplate {
        final Map<Long, Map<String, Object>> rows = new ConcurrentHashMap<>();
        final AtomicInteger id = new AtomicInteger(0);

        InMemoryJdbc() {
            super(new org.springframework.jdbc.datasource.DriverManagerDataSource());
        }

        void insertRow(String username, String operation, String entityType, String entityId, String createdAt) {
            long id = this.id.incrementAndGet();
            Map<String, Object> row = new LinkedHashMap<>();
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
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            long auditLogId = ((Number) args[args.length - 1]).longValue();
            if (!rows.containsKey(auditLogId)) {
                throw new org.springframework.dao.IncorrectResultSizeDataAccessException(
                        "no row for id=" + auditLogId, 0);
            }
            return new java.util.HashMap<>(rows.get(auditLogId));
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            long auditLogId = (Long) args[args.length - 1];
            String emptyHash = (String) args[0];
            long prevId = auditLogId - 1;
            if (!rows.containsKey(prevId) || rows.get(prevId).get("curr_hash") == null) {
                return requiredType.cast(emptyHash);
            }
            return requiredType.cast(rows.get(prevId).get("curr_hash"));
        }

        @Override
        public int update(String sql, Object... args) {
            long auditLogId = (Long) args[args.length - 1];
            if (!rows.containsKey(auditLogId)) return 0;
            rows.get(auditLogId).put("prev_hash", args[0]);
            rows.get(auditLogId).put("curr_hash", args[1]);
            rows.get(auditLogId).put("hash_algorithm", args[2]);
            return 1;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql) {
            return new java.util.ArrayList<>(new java.util.TreeMap<>(rows).values());
        }

        @Override
        public java.util.List<Map<String, Object>> queryForList(String sql, Object... args) {
            return new java.util.ArrayList<>(new java.util.TreeMap<>(rows).values());
        }
    }

    private AuditHashChainService service;
    private InMemoryJdbc jdbc;

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
        this.jdbc = new InMemoryJdbc();
        this.service = new AuditHashChainService(jdbc);
    }

    @Test
    @DisplayName("4 次 append 后哈希链一贯：前缀 H1 固定，后续可递归重算")
    void hashChainStableAfterFourAppends() {
        // 模拟 append 1
        jdbc.insertRow("alice", "LOGIN", "USER", "u1", "2026-09-02T10:00:00");
        service.stampHashChain(1L);

        // 模拟 append 2
        jdbc.insertRow("bob", "CREATE", "DOC", "d1", "2026-09-02T10:01:00");
        service.stampHashChain(2L);

        // 模拟 append 3
        jdbc.insertRow("alice", "UPDATE", "DOC", "d1", "2026-09-02T10:02:00");
        service.stampHashChain(3L);

        // 模拟 append 4
        jdbc.insertRow("charlie", "DELETE", "USER", "u2", "2026-09-02T10:03:00");
        service.stampHashChain(4L);

        // 验证：首条 prevHash = EMPTY_HASH
        Map<String, Object> row1 = jdbc.rows.get(1L);
        assertEquals(sha256(""), row1.get("prev_hash"));

        // 第二条 prevHash 应等于第一条 currHash
        Map<String, Object> row2 = jdbc.rows.get(2L);
        assertEquals(row1.get("curr_hash"), row2.get("prev_hash"));

        // 第三条 prevHash 应等于第二条 currHash
        Map<String, Object> row3 = jdbc.rows.get(3L);
        assertEquals(row2.get("curr_hash"), row3.get("prev_hash"));

        // 第四条 prevHash 应等于第三条 currHash
        Map<String, Object> row4 = jdbc.rows.get(4L);
        assertEquals(row3.get("curr_hash"), row4.get("prev_hash"));

        // 每条 currHash = SHA256(prevHash|username|operation|entity_type|entity_id|created_at)
        for (long i = 1; i <= 4; i++) {
            Map<String, Object> row = jdbc.rows.get(i);
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
            jdbc.insertRow("u" + i, "OP" + i, "TYPE" + i, "id" + i, "2026-09-02T10:0" + (i - 1) + ":00");
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
            jdbc.insertRow("u" + i, "OP" + i, "TYPE" + i, "id" + i, "2026-09-02T10:0" + (i - 1) + ":00");
            service.stampHashChain((long) i);
        }
        // 篡改第 2 条 username → 链断裂
        jdbc.rows.get(2L).put("username", "EVIL");

        Map<String, Object> result = service.verifyHashChain();
        assertFalse((Boolean) result.get("valid"));
        assertEquals(2L, ((Number) result.get("brokenAt")).longValue());
    }
}
