package com.chinacreator.gzcm.engine.kb.graph;

import com.chinacreator.gzcm.engine.kb.service.EcosKnowledgeGraphServiceImpl;
import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F04-10 图谱双形态做实验收测试（mvn -Dtest=GraphDualFormTest）。
 *
 * <p>对治 K-29 伪成功：旧 {@code syncToNeo4j} 固定返回 {@code ready_to_sync}（PG 218 节点 vs
 * Neo4j 44 仍报"准备同步"）。F04-10 要求：要么真写 Neo4j 投影并做 PG↔Neo4j 计数对账落
 * {@code kg_sync_log} 真结果，要么明确返回 {@code notAttempted}（PG 唯一权威形态）；
 * {@code ready_to_sync} 状态字面量全仓清除（源码 regex 断言）。</p>
 *
 * @author ECOS KB Team
 */
class GraphDualFormTest {

    // ── 工具：findSourceFile 从 CWD 向上回溯（兼容 surefire/IDE） ──

    private static Path findKbSourceRoot() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 12 && cur != null; i++) {
            Path candidate = cur.resolve("engine/kb-engine/kb-engine-impl/src/main/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cur = cur.getParent();
        }
        throw new AssertionError("未找到 kb-engine-impl 源码根（从 CWD 向上回溯 12 级失败）");
    }

    private static Path findSourceFile(String fileName) throws IOException {
        Path root = findKbSourceRoot();
        return Files.walk(root)
                .filter(p -> p.getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到源码文件 " + fileName));
    }

    // ── 构造辅助 ──

    private static EcosKnowledgeGraphServiceImpl buildService(JdbcTemplate jdbc, boolean switchOnWrite) {
        return new EcosKnowledgeGraphServiceImpl(jdbc, switchOnWrite);
    }

    private static void injectNeo4j(EcosKnowledgeGraphServiceImpl svc, Neo4jClient client)
            throws Exception {
        Field f = EcosKnowledgeGraphServiceImpl.class.getDeclaredField("neo4jClient");
        f.setAccessible(true);
        f.set(svc, client);
    }

    // ── 测试 1：真写 or 明确标记 notAttempted（不返回伪成功） ──

    /**
     * F04-10 核心断言：
     * (a) 开关关 → notAttempted（PG 唯一形态）
     * (b) 开关开但 Neo4j 不可用 → notAttempted
     * (c) 开关开 + Neo4j 可用 + 真写成功 → status=synced（非"准备"伪态）
     */
    @Test
    void syncEitherWritesOrMarksDegraded() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("graph_node"), eq(Integer.class))).thenReturn(5);
        when(jdbc.queryForObject(contains("graph_edge"), eq(Integer.class))).thenReturn(2);

        // (a) 开关关 → notAttempted
        EcosKnowledgeGraphServiceImpl svcOff = buildService(jdbc, false);
        Map<String, Object> rOff = svcOff.syncToNeo4j();
        assertEquals("notAttempted", rOff.get("status"),
                "开关关（standard 档）须返回 notAttempted，不得返回伪成功");
        assertEquals("PG_ONLY", rOff.get("graphForm"));
        assertFalse(String.valueOf(rOff.get("reason")).isBlank(),
                "notAttempted 须携带原因");

        // (b) 开关开，Neo4j 连不上 → notAttempted
        Neo4jClient staleClient = mock(Neo4jClient.class);
        when(staleClient.isAvailable()).thenReturn(false);
        EcosKnowledgeGraphServiceImpl svcStale = buildService(jdbc, true);
        injectNeo4j(svcStale, staleClient);
        Map<String, Object> rStale = svcStale.syncToNeo4j();
        assertEquals("notAttempted", rStale.get("status"),
                "Neo4j 不可用时须返回 notAttempted，不得伪装 ready");

        // (c) 开关开 + Neo4j 可用 + 真写 → synced
        Neo4jClient liveClient = mock(Neo4jClient.class);
        when(liveClient.isAvailable()).thenReturn(true);
        when(liveClient.verifyConnectivity()).thenReturn(true);
        when(liveClient.run(contains("MERGE"), anyMap())).thenReturn(List.of());
        when(liveClient.run(contains("MATCH (kg:EcosKgNode) RETURN count"), anyMap()))
                .thenReturn(List.of(Map.of("cnt", 5L)));
        when(liveClient.run(contains("MATCH (:EcosKgNode)-[r:"), anyMap()))
                .thenReturn(List.of(Map.of("cnt", 2L)));
        when(jdbc.queryForList(contains("FROM ecos_knowledge.graph_node")))
                .thenReturn(List.of(
                        Map.of("id", "n1", "label", "A", "nodeType", "T", "domain", "D"),
                        Map.of("id", "n2", "label", "B", "nodeType", "T", "domain", "D")));
        when(jdbc.queryForList(contains("FROM ecos_knowledge.graph_edge")))
                .thenReturn(List.of(
                        Map.of("sourceId", "n1", "targetId", "n2", "relType", "R1"),
                        Map.of("sourceId", "n2", "targetId", "n1", "relType", "R2")));

        EcosKnowledgeGraphServiceImpl svcLive = buildService(jdbc, true);
        injectNeo4j(svcLive, liveClient);
        Map<String, Object> rLive = svcLive.syncToNeo4j();
        assertEquals("synced", rLive.get("status"),
                "Neo4j 可用且真写成功后返回 synced，不得为 pseudo/trivial 状态");
        assertEquals("PG_NEO4J", rLive.get("graphForm"));
        // 验证 Neo4j.Client.run 确实被调用（真写，非 no-op）
        verify(liveClient, org.mockito.Mockito.atLeastOnce()).run(contains("MERGE"), anyMap());
    }

    // ── 测试 2：PG↔Neo4j 计数对账落在 kg_sync_log，且 reconciled 字段正确 ──

    @Test
    void pgNeo4jNodeCountReconcileAfterSync() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("graph_node"), eq(Integer.class))).thenReturn(3);
        when(jdbc.queryForObject(contains("graph_edge"), eq(Integer.class))).thenReturn(1);
        when(jdbc.queryForList(contains("FROM ecos_knowledge.graph_node")))
                .thenReturn(List.of(
                        Map.of("id", "n1", "label", "X", "nodeType", "T", "domain", "D"),
                        Map.of("id", "n2", "label", "Y", "nodeType", "T", "domain", "D"),
                        Map.of("id", "n3", "label", "Z", "nodeType", "T", "domain", "D")));
        when(jdbc.queryForList(contains("FROM ecos_knowledge.graph_edge")))
                .thenReturn(List.of(
                        Map.of("sourceId", "n1", "targetId", "n2", "relType", "LINK")));

        // Neo4j 副本与 PG 一致（reconciled=true）
        Neo4jClient client = mock(Neo4jClient.class);
        when(client.isAvailable()).thenReturn(true);
        when(client.verifyConnectivity()).thenReturn(true);
        when(client.run(contains("MERGE"), anyMap())).thenReturn(List.of());
        when(client.run(contains("MATCH (:EcosKgNode)-[r:"), anyMap()))
                .thenReturn(List.of(Map.of("cnt", 1L)));
        when(client.run(contains("RETURN count"), anyMap()))
                .thenReturn(List.of(Map.of("cnt", 3L)));

        EcosKnowledgeGraphServiceImpl svc = buildService(jdbc, true);
        injectNeo4j(svc, client);
        Map<String, Object> r = svc.syncToNeo4j();

        assertEquals("synced", r.get("status"));
        assertTrue((Boolean) r.get("reconciled"),
                "PG 节点数(3)==Neo4j 副本节点数(3) 时 reconciled 必须为 true");
        assertEquals(3, r.get("pgNodes"));
        assertEquals(3L, r.get("neo4jNodes"));
    }

    // ── 测试 3：ready_to_sync 字面量从源码全仓清除 ──

    @Test
    void noReadyToSyncLiteralRemains() throws IOException {
        Path implFile = findSourceFile("EcosKnowledgeGraphServiceImpl.java");
        String implSrc = Files.readString(implFile, StandardCharsets.UTF_8);
        assertFalse(implSrc.contains("ready_to_sync"),
                "EcosKnowledgeGraphServiceImpl 不得再含 ready_to_sync 字面量（F04-10 K-29 受除）");

        Path voFile = findSourceFile("KgNeo4jSyncResultVO.java");
        String voSrc = Files.readString(voFile, StandardCharsets.UTF_8);
        assertFalse(voSrc.contains("ready_to_sync"),
                "KgNeo4jSyncResultVO 不得再含 ready_to_sync 字面量");
    }
}
