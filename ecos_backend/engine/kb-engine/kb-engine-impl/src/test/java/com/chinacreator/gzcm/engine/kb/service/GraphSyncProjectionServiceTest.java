package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.EcosKnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.KgSyncService;
import com.chinacreator.gzcm.engine.kb.dao.EcosKnowledgeGraphDao;
import com.chinacreator.gzcm.engine.kb.dao.KgSyncLogDao;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 附录 A 第 15 行投影/对账/台账服务面门（详设 §7.2.3；与 {@code GraphDualFormTest} 同族但口径不同：
 * 后者钉既有 {@code syncToNeo4j} 双形态，本类钉<b>新增</b>的 {@code projectGraph / graphFormStatus}
 * 与台账腿 {@code runProjection / getLastSyncTime / getSyncLogsPage}）。
 *
 * <p>钉四件事：① 投影只有两种结局——真写并对账一致（{@code PROJECTED}）或本档位无投影能力
 * （{@code PG_ONLY} ＋ {@code notAttempted} ＋原因），禁「就绪待同步」伪成功；② 漂移与投影端
 * 读数不可读一律 500 {@code ECOS-KB-080}（前者已知不一致、后者未知，均不得以 200 糊过）；
 * ③ PG 读故障（{@code DataAccessException}）原样上浮 → advice 落 500 {@code ECOS-KB-050}，
 * 不被折成 {@code PG_ONLY}／空对账；④ 台账腿：成功写 {@code SUCCESS+report_json}、失败标
 * {@code FAILED} 且异常原样上抛、{@code traceId} 落库、分页钳制 1..200。</p>
 */
class GraphSyncProjectionServiceTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // ── projectGraph ────────────────────────────────────────────────────

    @Test
    @DisplayName("projectGraph 无投影客户端 ⇒ PG_ONLY + notAttempted=PG 节点数 + 原因（禁伪成功）")
    void projectGraphPgOnly() {
        EcosKnowledgeGraphDao dao = mock(EcosKnowledgeGraphDao.class);
        when(dao.loadProjectionNodes()).thenReturn(pgNodes(3));
        when(dao.loadProjectionEdges()).thenReturn(pgEdges(1));

        Map<String, Object> r = graphService(dao, null).projectGraph(false);

        assertEquals("PG_ONLY", r.get("graphForm"));
        assertEquals(0, r.get("attempted"));
        assertEquals(0, r.get("written"));
        assertEquals(3, r.get("notAttempted"), "未尝试数必须等于 PG 权威节点数");
        assertTrue(String.valueOf(r.get("reason")).contains("Neo4j 投影"),
                "原因必须点名无投影能力（禁空原因）");
        assertNull(r.get("reconcileDiff"), "未投影即无对账读数，不得伪造 matched");
    }

    @Test
    @DisplayName("projectGraph 可用 ⇒ 真写节点/边 + 逐类型对账一致 ⇒ PROJECTED")
    @SuppressWarnings("unchecked")
    void projectGraphWritesThenReconciles() {
        EcosKnowledgeGraphDao dao = mock(EcosKnowledgeGraphDao.class);
        when(dao.loadProjectionNodes()).thenReturn(pgNodes(2));
        when(dao.loadProjectionEdges()).thenReturn(pgEdges(1));
        when(dao.countNodesByType()).thenReturn(List.of(Map.of("nodeType", "PERSON", "c", 2L)));
        Neo4jClient neo = neoReturning(2L, 1L, List.of(Map.of("nodeType", "PERSON", "c", 2L)));

        Map<String, Object> r = graphService(dao, neo).projectGraph(false);

        assertEquals("PROJECTED", r.get("graphForm"));
        assertEquals(2, r.get("attempted"));
        assertEquals(2L, r.get("written"), "written 取投影端可读回节点数");
        assertEquals(0, r.get("notAttempted"));
        assertNull(r.get("reason"), "已真写则无未尝试原因");
        Map<String, Object> diff = (Map<String, Object>) r.get("reconcileDiff");
        assertEquals(Boolean.TRUE, diff.get("matched"));
        assertEquals(Boolean.TRUE, diff.get("nodesMatched"));
        assertEquals(Boolean.TRUE, diff.get("edgesMatched"));
        assertEquals(0L, diff.get("edgeSkipped"));
        Map<String, Object> person = (Map<String, Object>) ((Map<String, Object>) diff.get("byType")).get("PERSON");
        assertEquals(2L, person.get("pg"));
        assertEquals(2L, person.get("neo4j"));
        assertEquals(Boolean.TRUE, person.get("matched"));
        verify(neo, times(2)).writeBatch(anyList(), anyList());
        verify(neo, never()).write(anyString(), any());
    }

    @Test
    @DisplayName("projectGraph fullResync=true ⇒ 先清空投影子图再写（且只清一次）")
    void projectGraphFullResyncWipesFirst() {
        EcosKnowledgeGraphDao dao = mock(EcosKnowledgeGraphDao.class);
        when(dao.loadProjectionNodes()).thenReturn(pgNodes(1));
        when(dao.loadProjectionEdges()).thenReturn(pgEdges(0));
        when(dao.countNodesByType()).thenReturn(List.of(Map.of("nodeType", "PERSON", "c", 1L)));
        Neo4jClient neo = neoReturning(1L, 0L, List.of(Map.of("nodeType", "PERSON", "c", 1L)));

        graphService(dao, neo).projectGraph(true);

        verify(neo, times(1)).write(anyString(), any());
    }

    @Test
    @DisplayName("projectGraph 对账漂移 ⇒ 500 ECOS-KB-080（禁 200 + 空对账糊过）")
    void projectGraphDriftThrows080() {
        EcosKnowledgeGraphDao dao = mock(EcosKnowledgeGraphDao.class);
        when(dao.loadProjectionNodes()).thenReturn(pgNodes(2));
        when(dao.loadProjectionEdges()).thenReturn(pgEdges(1));
        when(dao.countNodesByType()).thenReturn(List.of(Map.of("nodeType", "PERSON", "c", 2L)));
        Neo4jClient neo = neoReturning(1L, 1L, List.of(Map.of("nodeType", "PERSON", "c", 1L)));

        KbErrorCodes.DomainException ex = assertThrows(KbErrorCodes.DomainException.class,
                () -> graphService(dao, neo).projectGraph(false));
        assertEquals(500, ex.getHttpStatus());
        assertEquals(KbErrorCodes.GRAPH_FORM_MISMATCH.code(), ex.getCode());
        assertTrue(ex.getMessage().contains("MISMATCH"), "message 必须显式标注 graphForm=MISMATCH");
        assertTrue(ex.getMessage().contains("PERSON"), "message 必须带逐类型差异（谜底可读）");
    }

    @Test
    @DisplayName("projectGraph 投影端计数不可读 ⇒ 同一 500 ECOS-KB-080（未知 ≠ 一致）")
    void projectGraphUnreadableCountsThrows080() {
        EcosKnowledgeGraphDao dao = mock(EcosKnowledgeGraphDao.class);
        when(dao.loadProjectionNodes()).thenReturn(pgNodes(1));
        when(dao.loadProjectionEdges()).thenReturn(pgEdges(0));
        Neo4jClient neo = mock(Neo4jClient.class);
        when(neo.isAvailable()).thenReturn(true);
        when(neo.runFirst(anyString(), any())).thenReturn(null);   // 计数查询无结果

        KbErrorCodes.DomainException ex = assertThrows(KbErrorCodes.DomainException.class,
                () -> graphService(dao, neo).projectGraph(false));
        assertEquals(KbErrorCodes.GRAPH_FORM_MISMATCH.code(), ex.getCode());
        assertTrue(ex.getMessage().contains("不可读"), "message 必须区分「读不到」与「不一致」");
    }

    @Test
    @DisplayName("projectGraph PG 读故障 ⇒ DataAccessException 原样上浮（不折成 PG_ONLY / 不发写入）")
    void projectGraphPgReadFailurePropagates() {
        EcosKnowledgeGraphDao dao = mock(EcosKnowledgeGraphDao.class);
        DataAccessResourceFailureException boom = new DataAccessResourceFailureException("graph_node down");
        when(dao.loadProjectionNodes()).thenThrow(boom);
        Neo4jClient neo = mock(Neo4jClient.class);
        when(neo.isAvailable()).thenReturn(true);

        assertThrows(DataAccessResourceFailureException.class,
                () -> graphService(dao, neo).projectGraph(false));
        verifyNoInteractions(neo);
    }

    // ── graphFormStatus ────────────────────────────────────────────────

    @Test
    @DisplayName("graphFormStatus 无投影 ⇒ PG_ONLY，diff.matched=null（未知禁写成 false）")
    @SuppressWarnings("unchecked")
    void graphFormStatusPgOnly() {
        EcosKnowledgeGraphDao dao = mock(EcosKnowledgeGraphDao.class);
        when(dao.countNodes()).thenReturn(4);
        when(dao.countEdges()).thenReturn(2);
        KgSyncService kgSync = mock(KgSyncService.class);
        when(kgSync.getLastSyncTime()).thenReturn(null);

        Map<String, Object> r = graphService(dao, null, kgSync).graphFormStatus();

        assertEquals("PG_ONLY", r.get("graphForm"));
        assertNull(r.get("lastSyncTime"), "无成功行即 null（禁编造时刻）");
        assertFalse(String.valueOf(r.get("reason")).isBlank(), "PG_ONLY 必须给原因");
        Map<String, Object> diff = (Map<String, Object>) r.get("diff");
        assertNull(diff.get("matched"), "读数不可得 ⇒ matched=null，禁冒充已知不一致");
        assertNull(diff.get("nodesNeo4j"));
        assertEquals(4L, diff.get("nodesPg"), "PG 权威数仍须给出");
        assertNull(r.get("mismatchCode"));
    }

    @Test
    @DisplayName("graphFormStatus 计数一致 ⇒ PROJECTED 且不带 mismatchCode")
    void graphFormStatusProjected() {
        EcosKnowledgeGraphDao dao = mock(EcosKnowledgeGraphDao.class);
        when(dao.countNodes()).thenReturn(2);
        when(dao.countEdges()).thenReturn(1);
        when(dao.countNodesByType()).thenReturn(List.of(Map.of("nodeType", "PERSON", "c", 2L)));
        KgSyncService kgSync = mock(KgSyncService.class);
        when(kgSync.getLastSyncTime()).thenReturn("2026-10-28T10:00:00.000+08:00");
        Neo4jClient neo = neoReturning(2L, 1L, List.of(Map.of("nodeType", "PERSON", "c", 2L)));

        Map<String, Object> r = graphService(dao, neo, kgSync).graphFormStatus();

        assertEquals("PROJECTED", r.get("graphForm"));
        assertEquals("2026-10-28T10:00:00.000+08:00", r.get("lastSyncTime"));
        assertNull(r.get("mismatchCode"), "一致态不得带漂移码");
        assertNull(r.get("reason"));
    }

    @Test
    @DisplayName("graphFormStatus 计数漂移 ⇒ MISMATCH + mismatchCode=080（读面仍 200，不抛）")
    @SuppressWarnings("unchecked")
    void graphFormStatusMismatch() {
        EcosKnowledgeGraphDao dao = mock(EcosKnowledgeGraphDao.class);
        when(dao.countNodes()).thenReturn(3);
        when(dao.countEdges()).thenReturn(1);
        when(dao.countNodesByType()).thenReturn(List.of(Map.of("nodeType", "PERSON", "c", 3L)));
        KgSyncService kgSync = mock(KgSyncService.class);
        when(kgSync.getLastSyncTime()).thenReturn("2026-10-28T10:00:00.000+08:00");
        Neo4jClient neo = neoReturning(2L, 1L, List.of(Map.of("nodeType", "PERSON", "c", 2L)));

        Map<String, Object> r = graphService(dao, neo, kgSync).graphFormStatus();

        assertEquals("MISMATCH", r.get("graphForm"), "漂移是被读对象，读面不得折成错误态");
        assertEquals(KbErrorCodes.GRAPH_FORM_MISMATCH.code(), r.get("mismatchCode"));
        Map<String, Object> diff = (Map<String, Object>) r.get("diff");
        assertEquals(Boolean.FALSE, diff.get("matched"));
        assertTrue(String.valueOf(r.get("reason")).contains("PERSON"), "reason 必须带差异类型");
    }

    @Test
    @DisplayName("graphFormStatus 投影端计数不可读 ⇒ PG_ONLY（不是 MISMATCH：未知≠不一致）")
    @SuppressWarnings("unchecked")
    void graphFormStatusUnreadableProjectionCounts() {
        EcosKnowledgeGraphDao dao = mock(EcosKnowledgeGraphDao.class);
        when(dao.countNodes()).thenReturn(1);
        when(dao.countEdges()).thenReturn(0);
        KgSyncService kgSync = mock(KgSyncService.class);
        when(kgSync.getLastSyncTime()).thenReturn(null);
        Neo4jClient neo = mock(Neo4jClient.class);
        when(neo.isAvailable()).thenReturn(true);
        when(neo.runFirst(anyString(), any())).thenReturn(null);

        Map<String, Object> r = graphService(dao, neo, kgSync).graphFormStatus();

        assertEquals("PG_ONLY", r.get("graphForm"));
        assertNull(r.get("mismatchCode"), "读数不可得不等于已确认漂移");
        assertTrue(String.valueOf(r.get("reason")).contains("不可读"));
        assertNull(((Map<String, Object>) r.get("diff")).get("matched"));
    }

    @Test
    @DisplayName("graphFormStatus 台账读故障 ⇒ 上浮（禁把「读不到」渲染成「从未同步」）")
    void graphFormStatusLedgerFailurePropagates() {
        EcosKnowledgeGraphDao dao = mock(EcosKnowledgeGraphDao.class);
        when(dao.countNodes()).thenReturn(0);
        when(dao.countEdges()).thenReturn(0);
        KgSyncService kgSync = mock(KgSyncService.class);
        when(kgSync.getLastSyncTime()).thenThrow(new DataAccessResourceFailureException("sync_log down"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> graphService(dao, null, kgSync).graphFormStatus());
    }

    // ── runProjection（台账腿）──────────────────────────────────────────

    @Test
    @DisplayName("runProjection 成功 ⇒ RUNNING→SUCCESS+report，traceId 落库，出参并回显 jobId/scope/traceId")
    @SuppressWarnings("unchecked")
    void runProjectionMarksSuccessAndEchoes() {
        MDC.put("traceId", "trace-77");
        KgSyncLogDao logDao = mock(KgSyncLogDao.class);
        EcosKnowledgeGraphService graph = mock(EcosKnowledgeGraphService.class);
        when(graph.projectGraph(false)).thenReturn(projectedMap());

        Map<String, Object> out = syncService(logDao, graph).runProjection(null, false);

        verify(logDao).insertLog(eq("ALL"), eq("NEO4J_SYNC"), startsWith("graph-sync-"), eq("RUNNING"),
                eq(0), eq(0), eq(0), isNull(), eq("trace-77"));
        verify(logDao).markLogSuccessWithReport(startsWith("graph-sync-"), eq(2), eq(1), anyString());
        verify(logDao, never()).markLogFailed(anyString(), anyString());

        assertEquals("PROJECTED", out.get("graphForm"), "投影结果键必须原样透传");
        assertEquals("ALL", out.get("scope"), "空 scope 归一为 ALL");
        assertEquals("trace-77", out.get("traceId"));
        assertNotNull(out.get("jobId"));
        assertTrue(((String) out.get("jobId")).startsWith("graph-sync-"));
        assertTrue(((Map<String, Object>) out.get("reconcileDiff")).containsKey("matched"));
    }

    @Test
    @DisplayName("runProjection 失败 ⇒ 台账标 FAILED 且异常原样上抛（禁吞成 200）")
    void runProjectionMarksFailedAndRethrows() {
        KgSyncLogDao logDao = mock(KgSyncLogDao.class);
        EcosKnowledgeGraphService graph = mock(EcosKnowledgeGraphService.class);
        KbErrorCodes.DomainException drift =
                KbErrorCodes.GRAPH_FORM_MISMATCH.exception("PG↔Neo4j 计数不一致（graphForm=MISMATCH）");
        when(graph.projectGraph(false)).thenThrow(drift);

        KbErrorCodes.DomainException ex = assertThrows(KbErrorCodes.DomainException.class,
                () -> syncService(logDao, graph).runProjection("ALL", false));

        assertEquals(KbErrorCodes.GRAPH_FORM_MISMATCH.code(), ex.getCode(), "异常必须原样上浮");
        verify(logDao).markLogFailed(startsWith("graph-sync-"), eq(drift.getMessage()));
        verify(logDao, never()).markLogSuccessWithReport(anyString(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("runProjection scope 超列宽 ⇒ 只截台账展示值，出参仍回显原值（不改投影范围）")
    void runProjectionTruncatesLedgerScopeOnly() {
        KgSyncLogDao logDao = mock(KgSyncLogDao.class);
        EcosKnowledgeGraphService graph = mock(EcosKnowledgeGraphService.class);
        when(graph.projectGraph(true)).thenReturn(projectedMap());
        String longScope = "ONTOLOGY-SNAPSHOT-0001-EXTENDED";

        Map<String, Object> out = syncService(logDao, graph).runProjection(longScope, true);

        verify(logDao).insertLog(eq(longScope.substring(0, 20)), eq("NEO4J_SYNC"),
                startsWith("graph-sync-"), eq("RUNNING"), anyInt(), anyInt(), anyInt(), isNull(), any());
        assertEquals(longScope, out.get("scope"), "出参回显原值（声明的范围如实回显）");
    }

    // ── getLastSyncTime / getSyncLogsPage ──────────────────────────────

    @Test
    @DisplayName("getLastSyncTime 无成功行 ⇒ null；有行 ⇒ ISO-8601 带时区")
    void getLastSyncTimeFormatsIso() {
        KgSyncLogDao logDao = mock(KgSyncLogDao.class);
        KgSyncServiceImpl svc = syncService(logDao, mock(EcosKnowledgeGraphService.class));

        when(logDao.lastSuccessTime()).thenReturn(null);
        assertNull(svc.getLastSyncTime(), "无成功行必须 null（禁编造时刻）");

        when(logDao.lastSuccessTime()).thenReturn(Timestamp.from(java.time.Instant.parse("2026-10-28T02:00:00Z")));
        String iso = svc.getLastSyncTime();
        assertNotNull(iso);
        assertTrue(iso.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(\\+|-)\\d{2}:\\d{2}"),
                "lastSyncTime 必须是带时区的 ISO-8601: " + iso);
    }

    @Test
    @DisplayName("getLastSyncTime 读故障 ⇒ 上浮（禁折成 null 冒充「从未同步」）")
    void getLastSyncTimePropagatesFailure() {
        KgSyncLogDao logDao = mock(KgSyncLogDao.class);
        when(logDao.lastSuccessTime()).thenThrow(new DataAccessResourceFailureException("sync_log down"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> syncService(logDao, mock(EcosKnowledgeGraphService.class)).getLastSyncTime());
    }

    @Test
    @DisplayName("getSyncLogsPage 钳制 page≥1 / size∈[1,200]，条目带 traceId 且 status 归一")
    @SuppressWarnings("unchecked")
    void getSyncLogsPageClampsAndProjects() {
        KgSyncLogDao logDao = mock(KgSyncLogDao.class);
        when(logDao.getSyncLogsPage(0, 200)).thenReturn(List.of(logRow()));
        when(logDao.countSyncLog()).thenReturn(7);

        Map<String, Object> out = syncService(logDao, mock(EcosKnowledgeGraphService.class))
                .getSyncLogsPage(0, 999);

        assertEquals(1, out.get("page"), "page<1 钳到 1");
        assertEquals(200, out.get("size"), "size 上限 200");
        assertEquals(7, out.get("total"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) out.get("items");
        assertEquals(1, items.size());
        assertEquals("trace-9", items.get(0).get("traceId"), "条目必须含 traceId");
        assertEquals("success", items.get(0).get("status"), "status 归一到前端词表");
        assertEquals("graph-sync-9", items.get(0).get("jobId"));
    }

    @Test
    @DisplayName("getSyncLogsPage 读故障 ⇒ 上浮（禁返回空列表冒充「没有日志」）")
    void getSyncLogsPagePropagatesFailure() {
        KgSyncLogDao logDao = mock(KgSyncLogDao.class);
        when(logDao.getSyncLogsPage(anyInt(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("sync_log down"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> syncService(logDao, mock(EcosKnowledgeGraphService.class)).getSyncLogsPage(1, 20));
    }

    // ── fixtures ────────────────────────────────────────────────────────

    private static EcosKnowledgeGraphServiceImpl graphService(EcosKnowledgeGraphDao dao, Neo4jClient neo) {
        return graphService(dao, neo, mock(KgSyncService.class));
    }

    private static EcosKnowledgeGraphServiceImpl graphService(EcosKnowledgeGraphDao dao, Neo4jClient neo,
                                                             KgSyncService kgSync) {
        return new EcosKnowledgeGraphServiceImpl(dao, kgSync, provider(neo));
    }

    /** provider 语义与服务构造一致：mock provider 返回 null 表示「缺省 provider」。 */
    private static ObjectProvider<Neo4jClient> provider(Neo4jClient client) {
        ObjectProvider<Neo4jClient> prov = mock(ObjectProvider.class);
        when(prov.getIfAvailable()).thenReturn(client);
        return prov;
    }

    private static KgSyncServiceImpl syncService(KgSyncLogDao logDao, EcosKnowledgeGraphService graph) {
        return new KgSyncServiceImpl(logDao, null, null, null, graph);
    }

    private static Neo4jClient neoReturning(long nodes, long edges, List<Map<String, Object>> byType) {
        Neo4jClient neo = mock(Neo4jClient.class);
        when(neo.isAvailable()).thenReturn(true);
        when(neo.runFirst(anyString(), any())).thenAnswer(inv -> {
            String cypher = inv.getArgument(0);
            return Map.<String, Object>of("c", cypher.contains("count(n)") ? nodes : edges);
        });
        when(neo.run(anyString(), any())).thenReturn(byType);
        return neo;
    }

    private static List<Map<String, Object>> pgNodes(int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(Map.of("id", "n" + i, "label", "节点" + i, "nodeType", "PERSON", "domain", "HR"));
        }
        return rows;
    }

    private static List<Map<String, Object>> pgEdges(int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(Map.of("id", "e" + i, "sourceId", "n0", "targetId", "n1", "type", "KNOWS"));
        }
        return rows;
    }

    private static Map<String, Object> projectedMap() {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("nodesPg", 2L);
        diff.put("nodesNeo4j", 2L);
        diff.put("nodesMatched", true);
        diff.put("edgesPg", 1L);
        diff.put("edgesNeo4j", 1L);
        diff.put("edgesMatched", true);
        diff.put("edgeSkipped", 0L);
        diff.put("byType", Map.of("PERSON", Map.of("pg", 2L, "neo4j", 2L, "matched", true)));
        diff.put("matched", true);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("graphForm", "PROJECTED");
        m.put("attempted", 2);
        m.put("written", 2);
        m.put("notAttempted", 0);
        m.put("reason", null);
        m.put("reconcileDiff", diff);
        return m;
    }

    private static Map<String, Object> logRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobId", "graph-sync-9");
        row.put("objectType", "ALL");
        row.put("operation", "NEO4J_SYNC");
        row.put("status", "SUCCESS");
        row.put("progress", 100);
        row.put("nodes", 2);
        row.put("edges", 1);
        row.put("message", null);
        row.put("timestamp", "2026-10-28 10:00:00.0");
        row.put("finishedAt", "2026-10-28 10:00:01.0");
        row.put("traceId", "trace-9");
        return row;
    }
}
