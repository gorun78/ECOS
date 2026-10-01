package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.KgSyncService;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F04-10 图谱双形态做实 — 验收测试（详细设计-04 二·F04-10，A 章验收标识）。
 *
 * <p>纯单测（无 Spring 上下文）：PG 计数走 {@link JdbcTemplate} mock，Neo4j 走
 * {@link Neo4jClient} mock。</p>
 *
 * <ul>
 *   <li>{@code syncEitherWritesOrMarksDegraded} — Neo4j 不可用 → 显式 {@code notAttempted} + 降级标记；可用 → 真投影 + 对账；</li>
 *   <li>{@code pgNeo4jNodeCountReconcileAfterSync} — PG↔Neo4j 计数一致 → {@code synced}，不一致 → {@code reconcile_drift} + {ECOS-KB-080}；</li>
 *   <li>{@code noReadyToSyncLiteralRemains} — 源码 regex 扫描，无任何 {@code ready_to_sync} 字面量残留。</li>
 * </ul>
 */
class GraphDualFormTest {

    /** 源码真实路径（模块内相对定位，不依赖仓库根）。 */
    private static final Path MAIN_SRC =
            Path.of("src/main/java").toAbsolutePath();

    // ── Neo4j 不可用（standard 档）→ 显式降级不投影 ────────────────
    @Test
    void syncEitherWritesOrMarksDegraded() {
        // 分支 1：Neo4j 投影不可用（client 为 null，standard 档）
        JdbcTemplate jdbcNoNeo4j = mock(JdbcTemplate.class);
        Integer nc = 7;
        jdbcSetNodeAndEdgeCount(jdbcNoNeo4j, nc, 3);
        EcosKnowledgeGraphServiceImpl svc =
                new EcosKnowledgeGraphServiceImpl(jdbcNoNeo4j, mock(KgSyncService.class), null);

        Map<String, Object> r = svc.syncToNeo4j();
        assertEquals("notAttempted", r.get("status"), "Neo4j 不可用必须显式 notAttempted（禁伪成功）");
        assertEquals(Boolean.TRUE, r.get("degraded"), "降级必须显式 degrade=true");
        assertTrue(String.valueOf(r.get("degradeReason")).length() > 0, "降级必须给出原因");
        assertEquals(nc, r.get("pgNodeCount"), "PG 为权威形态，其计数必须回填");

        // 分支 2：Neo4j 完全缺失（client 缺省 provider）
        EcosKnowledgeGraphServiceImpl svcNullProvider =
                new EcosKnowledgeGraphServiceImpl(jdbcNoNeo4j, mock(KgSyncService.class), mock(ObjectProvider.class));
        Map<String, Object> r2 = svcNullProvider.syncToNeo4j();
        assertEquals("notAttempted", r2.get("status"));
        assertEquals(Boolean.TRUE, r2.get("degraded"));
    }

    // ── PG↔Neo4j 计数对账（一致→synced / 不一致→drift+错误码）────────
    @Test
    @SuppressWarnings("unchecked")
    void pgNeo4jNodeCountReconcileAfterSync() {
        int pgNodes = 12;
        int pgEdges = 20;

        // 一致：Neo4j 计数 = PG → synced
        JdbcTemplate jdbcA = mock(JdbcTemplate.class);
        jdbcSetNodeAndEdgeCount(jdbcA, pgNodes, pgEdges);
        Neo4jClient neoA = mock(Neo4jClient.class);
        when(neoA.isAvailable()).thenReturn(true);
        when(neoA.runFirst(anyString(), any())).thenAnswer(inv -> {
            String cy = (String) inv.getArgument(0);
            long c = cy.contains("count(n)") ? pgNodes : pgEdges;
            return Map.<String, Object>of("c", c);
        });
        EcosKnowledgeGraphServiceImpl svcA =
                new EcosKnowledgeGraphServiceImpl(jdbcA, mock(KgSyncService.class), provider(neoA));
        Map<String, Object> ok = svcA.syncToNeo4j();
        assertEquals("synced", ok.get("status"), "计数一致应判 synced");
        assertEquals(Boolean.FALSE, ok.get("degraded"));
        Map<String, Object> reconA = (Map<String, Object>) ok.get("reconcile");
        assertEquals(Boolean.TRUE, reconA.get("matched"));
        assertEquals(Integer.valueOf(pgNodes), reconA.get("pgNodes"));
        assertEquals(Long.valueOf(pgNodes), reconA.get("neo4jNodes"));

        // 不一致：Neo4j < PG → reconcile_drift + GRAPH_FORM_MISMATCH
        int neoNodesDrift = 5;
        JdbcTemplate jdbcB = mock(JdbcTemplate.class);
        jdbcSetNodeAndEdgeCount(jdbcB, pgNodes, pgEdges);
        Neo4jClient neoB = mock(Neo4jClient.class);
        when(neoB.isAvailable()).thenReturn(true);
        when(neoB.runFirst(anyString(), any())).thenAnswer(inv -> {
            String cy = (String) inv.getArgument(0);
            long c = cy.contains("count(n)") ? neoNodesDrift : pgEdges;
            return Map.<String, Object>of("c", c);
        });
        EcosKnowledgeGraphServiceImpl svcB =
                new EcosKnowledgeGraphServiceImpl(jdbcB, mock(KgSyncService.class), provider(neoB));
        Map<String, Object> drift = svcB.syncToNeo4j();
        assertEquals("reconcile_drift", drift.get("status"), "计数不一致必须显式 reconcile_drift");
        assertEquals(KbErrorCodes.GRAPH_FORM_MISMATCH.code(), drift.get("mismatchCode"),
                "形态不一致必须带 ECOS-KB-080 码（对账差异不吞）");
        Map<String, Object> reconB = (Map<String, Object>) drift.get("reconcile");
        assertEquals(Boolean.FALSE, reconB.get("matched"));
    }

    // ── ready_to_sync 伪成功字面量全仓清除（源码 regex 断言）────────
    @Test
    void noReadyToSyncLiteralRemains() throws IOException {
        Pattern lit = Pattern.compile("ready_to_sync");
        List<String> offenders = Files.walk(MAIN_SRC)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .flatMap(p -> {
                    try {
                        return Files.readAllLines(p, StandardCharsets.UTF_8)
                                .stream().map(l -> l.replaceAll("//.*$", "").replaceAll(".*//", ""));
                    } catch (IOException e) {
                        return Stream.<String>empty();
                    }
                })
                // 剔除 Javadoc 注释行（含 {@code ready_to_sync} 的说明性文字），只保留真实代码行
                .filter(l -> !l.contains("*"))
                .filter(l -> lit.matcher(l).find())
                .toList();
        assertTrue(offenders.isEmpty(),
                "kb 主源码发现 ready_to_sync 伪成功字面量残留（F04-10 伪成功清零）: " + offenders);
    }

    // ── helpers ─────────────────────────────────────────────

    /** 两条 COUNT 查询绑定 PG 节点/边数（顺序敏感，同服务实现内先后调用）。 */
    private void jdbcSetNodeAndEdgeCount(JdbcTemplate jdbc, int nodes, int edges) {
        lenient().when(jdbc.queryForObject(anyString(), any(Class.class)))
                .thenAnswer(inv -> ((String) inv.getArgument(0)).contains("graph_node") ? nodes : edges);
    }

    /** 构造返回给定 Neo4jClient 的 ObjectProvider（同包可访问工厂无需真实 Spring）。 */
    private ObjectProvider<Neo4jClient> provider(Neo4jClient client) {
        ObjectProvider<Neo4jClient> prov = mock(ObjectProvider.class);
        when(prov.getIfAvailable()).thenReturn(client);
        return prov;
    }
}
