package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F01-06 / W29 — 审计 Kafka 化后的消费落库与哈希链路完整性。
 *
 * <p>本测试锁定审计链 <b>消费侧</b> 两条契约：
 * <ol>
 *   <li>顺序消费 1..N 条 audit 事件后，哈希链 H1→H2→…→HN 严格衔接
 *       （前一条 currHash = 后一条 prevHash）；</li>
 *   <li>消费过程中若某条被篡改（模拟 Kafka 重放/重复重投递乱序后回写），
 *       {@link AuditHashChainService#verifyHashChain()} 应
 *       detect: valid=false + brokenAt 定位到首条断链 id</li>
 * </ol>
 * 使用内存 JdbcTemplate（同包同 util 口径），不依赖 Kafka / DB。
 * 幂等语义：同一 auditLogId 二次 stampHashChain 被 <b>忽略</b>（currHash 保持首写）。</p>
 */
class AuditChainConsumerTest {

    private static class InMemoryJdbc extends JdbcTemplate {
        final Map<Long, Map<String, Object>> rows = new ConcurrentHashMap<>();
        final AtomicInteger id = new AtomicInteger(0);

        InMemoryJdbc() {
            super(new DriverManagerDataSource());
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
            long id = ((Number) args[args.length - 1]).longValue();
            if (!rows.containsKey(id)) {
                throw new IncorrectResultSizeDataAccessException("no row id=" + id, 0);
            }
            return new java.util.HashMap<>(rows.get(id));
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            long id = (Long) args[args.length - 1];
            String emptyHash = (String) args[0];
            long prevId = id - 1;
            if (!rows.containsKey(prevId) || rows.get(prevId).get("curr_hash") == null) {
                return requiredType.cast(emptyHash);
            }
            return requiredType.cast(rows.get(prevId).get("curr_hash"));
        }

        @Override
        public int update(String sql, Object... args) {
            long id = (Long) args[args.length - 1];
            if (!rows.containsKey(id)) return 0;
            Map<String, Object> row = rows.get(id);
            // 幂等：若 currHash 已写过，重投递不覆盖
            if (row.get("curr_hash") != null) {
                return 0;
            }
            row.put("prev_hash", args[0]);
            row.put("curr_hash", args[1]);
            row.put("hash_algorithm", args[2]);
            return 1;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql) {
            return new ArrayList<>(new TreeMap<>(rows).values());
        }

        @Override
        public java.util.List<Map<String, Object>> queryForList(String sql, Object... args) {
            return new ArrayList<>(new TreeMap<>(rows).values());
        }
    }

    private InMemoryJdbc jdbc;
    private AuditHashChainService chain;

    @BeforeEach
    void setup() {
        jdbc = new InMemoryJdbc();
        chain = new AuditHashChainService(jdbc);
    }

    @Test
    @DisplayName("顺序消费 5 条 audit → 哈希链前缀一致 + prev=上一 curr")
    void orderedConsumption_producesIntactChain() {
        String[] ops = {"LOGIN", "CREATE", "CLONE", "EXPORT", "DELETE"};
        for (int i = 1; i <= 5; i++) {
            jdbc.insertRow("u" + i, ops[i - 1], "DOC", "d" + i, "2026-09-02T10:0" + (i - 1) + ":00");
            chain.stampHashChain((long) i);
        }
        Map<String, Object> verify = chain.verifyHashChain();
        assertTrue((Boolean) verify.get("valid"), "5 条顺序消费后 verify 必须 valid=true");
        assertEquals(5, ((Number) verify.get("totalChecked")).intValue());
        // brokenAt 由 verifyHashChainRange 定义为 List<Long>，valid=true → 空列表
        assertTrue(verify.get("brokenAt") instanceof List<?>
                && ((List<?>) verify.get("brokenAt")).isEmpty(),
                "valid=true 时 brokenAt 应为空列表");

        Map<String, Object> prev = jdbc.rows.get(1L);
        Map<String, Object> first = jdbc.rows.get(1L);
        assertEquals(prev.get("curr_hash"), first.get("curr_hash"));
        for (long i = 2; i <= 5; i++) {
            assertEquals(jdbc.rows.get(i - 1).get("curr_hash"), jdbc.rows.get(i).get("prev_hash"),
                    "row " + i + " prev_hash 必须衔接 row " + (i - 1) + " curr_hash");
        }
    }

    @Test
    @DisplayName("Kafka 重复投递（同一 auditLogId stampHashChain 二次）→ 幂等，链不重算")
    void duplicateDelivery_isIdempotent() {
        jdbc.insertRow("u1", "LOGIN", "USER", "u1", "2026-09-02T10:00:00");
        chain.stampHashChain(1L);
        String firstHash = (String) jdbc.rows.get(1L).get("curr_hash");

        // 模拟 Kafa 重投（DltConsumer/audit-retry 语义：再次 stampHashChain）
        chain.stampHashChain(1L);
        assertEquals(firstHash, jdbc.rows.get(1L).get("curr_hash"),
                "重复投递 auditLogId=1 后 curr_hash 必须保持首写值（幂等）");

        jdbc.insertRow("u2", "UPDATE", "USER", "u1", "2026-09-02T10:01:00");
        chain.stampHashChain(2L);
        Map<String, Object> verify = chain.verifyHashChain();
        assertTrue((Boolean) verify.get("valid"), "幂等后链完整性必须保持");
    }

    @Test
    @DisplayName("消费链中一条被篡改 → verifyHashChain 定位 brokenAt 且 valid=false")
    void tamperedConsumer_entryBreaksChain() {
        for (int i = 1; i <= 4; i++) {
            jdbc.insertRow("u" + i, "OP" + i, "TYPE" + i, "id" + i, "2026-09-02T10:0" + (i - 1) + ":00");
            chain.stampHashChain((long) i);
        }
        // 模拟 Kafka 落库时篡改 operation 字段
        jdbc.rows.get(3L).put("operation", "EVIL_OP");

        Map<String, Object> verify = chain.verifyHashChain();
        assertNotNull(verify, "verify 结果不可空");
        assertFalse((Boolean) verify.get("valid"), "篡改后 verify 必须 valid=false");
        List<?> broken = (List<?>) verify.get("brokenAt");
        assertEquals(1, broken.size(), "brokenAt 应恰好定位首条断链 (3L)");
        assertEquals(3L, ((Number) broken.get(0)).longValue(),
                "brokenAt[0] 应指向篡改的行 id=3");
    }
}
