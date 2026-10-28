package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.EcosKnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.KgSyncService;
import com.chinacreator.gzcm.engine.kb.dto.GraphFormTypeDiffVO;
import com.chinacreator.gzcm.engine.kb.dto.GraphReconcileDiffVO;
import com.chinacreator.gzcm.engine.kb.dto.GraphSyncLogListVO;
import com.chinacreator.gzcm.engine.kb.dto.GraphSyncRequest;
import com.chinacreator.gzcm.engine.kb.dto.GraphSyncResultVO;
import com.chinacreator.gzcm.engine.kb.dto.GraphSyncStatusVO;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /api/v1/knowledge/graph/sync} 三端点契约门（附录 A 第 15 行；详设 §7.2.3「投影/对账/诚实」）。
 *
 * <p>钉六件事：① POST 成功出参键面逐字承 {@code {attempted,written,notAttempted,reason,reconcileDiff}}
 * 且 {@code PG_ONLY} 时 {@code notAttempted}=PG 节点数、{@code reason} 必非空（「未尝试」≠「失败」）；
 * ② 入参缺省（body 为空 / {@code fullResync} 缺省）按 {@code ALL}＋不清空子图下发；
 * ③ 投影漂移／投影计数不可读的 {@code DomainException} 以 500 {@code ECOS-KB-080} <b>原样上浮</b>
 * （控制器禁宽 catch 折叠成 200）；④ {@code /status} 三态互不折叠：漂移仍 200 且带
 * {@code mismatchCode}，{@code PG_ONLY} 的 {@code diff.matched} 为 {@code null}（未知 ≠ 不一致）；
 * ⑤ {@code /logs} 分页原样透传且条目含 {@code traceId}；⑥ 台账/PG 读故障上浮（README 口径：
 * 500 {@code ECOS-KB-050} 由 advice 渲染，控制器不自造信封）。</p>
 *
 * <p>harness 与 {@code KnowledgeExtractRunsContractTest} 同族：Mockito + 直调 Controller 看
 * {@code getStatusCode()}——common-api 静态棘轮门对「形态对、状态值错」结构性失明。</p>
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeGraphSyncContractTest {

    @Mock private KgSyncService kgSyncService;
    @Mock private EcosKnowledgeGraphService graphService;

    private KnowledgeGraphSyncController controller() {
        return new KnowledgeGraphSyncController(kgSyncService, graphService);
    }

    private static void assertStatusAndCode(ResponseEntity<? extends ApiResponse<?>> resp,
                                            int expectedStatus,
                                            String expectedMachineCode) {
        assertEquals(expectedStatus, resp.getStatusCode().value(), "状态行必须等于契约期望值");
        ApiResponse<?> body = resp.getBody();
        assertNotNull(body, "ResponseEntity 必须携带信封");
        // 信封 code 口径：成功恒为 ApiResponse.CODE_SUCCESS(0)，失败恒与状态行同号。
        assertEquals(expectedStatus < 400 ? ApiResponse.CODE_SUCCESS : expectedStatus, body.getCode(),
                "信封 code：成功取 CODE_SUCCESS、失败与状态行同号");
        if (expectedMachineCode != null) {
            assertEquals(expectedMachineCode, body.getErrorCode(), "机读码必须取 KbErrorCodes 单源");
        }
    }

    // ── ① POST 成功：键面齐全 + PG_ONLY 诚实口径 ──────────────────────────

    @Test
    @DisplayName("POST 空 body ⇒ 200，PG_ONLY 带 notAttempted 与 reason，attempted/written 为 0")
    void triggerWithoutBodyReturnsPgOnlyHonestly() {
        when(kgSyncService.runProjection(null, false)).thenReturn(pgOnly());

        ResponseEntity<ApiResponse<GraphSyncResultVO>> resp = controller().triggerGraphSync(null);
        assertStatusAndCode(resp, 200, null);

        GraphSyncResultVO vo = resp.getBody().getData();
        assertEquals("PG_ONLY", vo.getGraphForm());
        assertEquals(0, vo.getAttempted(), "未尝试投影 ⇒ attempted 必须为 0（禁伪成功）");
        assertEquals(0, vo.getWritten(), "未写入 ⇒ written 必须为 0");
        assertEquals(5, vo.getNotAttempted(), "未尝试数必须等于 PG 权威节点数");
        assertNotNull(vo.getReason(), "PG_ONLY 必须给出原因（禁静默降级）");
        assertFalse(vo.getReason().isBlank(), "原因不得为空串");
        assertNull(vo.getReconcileDiff(), "未投影 ⇒ 无对账读数（不得伪造匹配）");
        assertEquals("graph-sync-1", vo.getJobId(), "台账 jobId 必须回传");
        assertEquals("ALL", vo.getScope(), "scope 缺省回显 ALL");
        assertEquals("trace-1", vo.getTraceId(), "台账 traceId 必须回传");
    }

    @Test
    @DisplayName("POST 成功（PROJECTED）⇒ reconcileDiff 键面与 byType 逐类型可读")
    void triggerProjectedCarriesReconcileDiff() {
        when(kgSyncService.runProjection("ONTOLOGY", true)).thenReturn(projected());
        GraphSyncRequest req = new GraphSyncRequest();
        req.setScope("ONTOLOGY");
        req.setFullResync(Boolean.TRUE);

        ResponseEntity<ApiResponse<GraphSyncResultVO>> resp = controller().triggerGraphSync(req);
        assertStatusAndCode(resp, 200, null);

        GraphSyncResultVO vo = resp.getBody().getData();
        assertEquals("PROJECTED", vo.getGraphForm());
        assertEquals(2, vo.getAttempted());
        assertEquals(2, vo.getWritten());
        assertEquals(0, vo.getNotAttempted());
        assertNull(vo.getReason(), "已投影且对账一致 ⇒ 无未尝试原因");
        GraphReconcileDiffVO diff = vo.getReconcileDiff();
        assertNotNull(diff, "已投影必须带对账明细");
        assertEquals(Boolean.TRUE, diff.getMatched());
        assertEquals(0L, diff.getEdgeSkipped());
        GraphFormTypeDiffVO person = diff.getByType().get("PERSON");
        assertNotNull(person, "逐类型对账必须可读（byType 反序列化到强类型）");
        assertEquals(2L, person.getPg());
        assertEquals(Boolean.TRUE, person.getMatched());
        verify(kgSyncService).runProjection("ONTOLOGY", true);
    }

    @Test
    @DisplayName("POST 请求体 fullResync 为 null ⇒ 按 false 下发（不清空投影子图）")
    void triggerNullFullResyncDefaultsFalse() {
        when(kgSyncService.runProjection("ALL", false)).thenReturn(pgOnly());
        GraphSyncRequest req = new GraphSyncRequest();
        req.setScope("ALL");

        controller().triggerGraphSync(req);
        verify(kgSyncService).runProjection("ALL", false);
    }

    // ── ② 漂移 / 读数不可读 ⇒ 500 ECOS-KB-080 原样上浮 ────────────────────

    @Test
    @DisplayName("POST 对账漂移 ⇒ DomainException 以 500/ECOS-KB-080 上浮（禁折叠成 200 伪成功）")
    void triggerDriftPropagates080() {
        when(kgSyncService.runProjection(null, false)).thenThrow(
                KbErrorCodes.GRAPH_FORM_MISMATCH.exception("PG↔Neo4j 计数不一致（graphForm=MISMATCH）"));

        KbErrorCodes.DomainException ex = assertThrows(KbErrorCodes.DomainException.class,
                () -> controller().triggerGraphSync(null));
        assertEquals(500, ex.getHttpStatus(), "形态不一致必须 500");
        assertEquals(KbErrorCodes.GRAPH_FORM_MISMATCH.code(), ex.getCode(), "机读码必须是 ECOS-KB-080");
    }

    @Test
    @DisplayName("POST 投影端计数不可读 ⇒ 同一 500/ECOS-KB-080（形态不可信不降级为 PG_ONLY）")
    void triggerUnreadableProjectionCountsPropagates080() {
        when(kgSyncService.runProjection(null, false)).thenThrow(
                KbErrorCodes.GRAPH_FORM_MISMATCH.exception("Neo4j 投影计数不可读（graphForm=MISMATCH）"));

        KbErrorCodes.DomainException ex = assertThrows(KbErrorCodes.DomainException.class,
                () -> controller().triggerGraphSync(null));
        assertEquals(KbErrorCodes.GRAPH_FORM_MISMATCH.code(), ex.getCode());
    }

    // ── ③ /status 三态互不折叠 ───────────────────────────────────────────

    @Test
    @DisplayName("GET /status 一致 ⇒ 200 PROJECTED，diff 齐全且无 mismatchCode")
    void statusProjected() {
        when(graphService.graphFormStatus()).thenReturn(statusMap("PROJECTED", null, projected().get("reconcileDiff")));

        ResponseEntity<ApiResponse<GraphSyncStatusVO>> resp = controller().getGraphSyncStatus();
        assertStatusAndCode(resp, 200, null);

        GraphSyncStatusVO vo = resp.getBody().getData();
        assertEquals("PROJECTED", vo.getGraphForm());
        assertEquals("2026-10-28T10:00:00.000+08:00", vo.getLastSyncTime(), "lastSyncTime 必须来自台账单源");
        assertNotNull(vo.getDiff());
        assertEquals(Boolean.TRUE, vo.getDiff().getMatched());
        assertNull(vo.getMismatchCode(), "一致态不得带漂移码");
    }

    @Test
    @DisplayName("GET /status 漂移 ⇒ 仍 200（漂移是被读对象）+ graphForm=MISMATCH + mismatchCode=080")
    void statusMismatchIsStillOk() {
        when(graphService.graphFormStatus()).thenReturn(statusMap("MISMATCH",
                KbErrorCodes.GRAPH_FORM_MISMATCH.code(), mismatchDiff()));

        ResponseEntity<ApiResponse<GraphSyncStatusVO>> resp = controller().getGraphSyncStatus();
        assertStatusAndCode(resp, 200, null);

        GraphSyncStatusVO vo = resp.getBody().getData();
        assertEquals("MISMATCH", vo.getGraphForm(), "读面不得把漂移折成错误态");
        assertEquals(KbErrorCodes.GRAPH_FORM_MISMATCH.code(), vo.getMismatchCode());
        assertEquals(Boolean.FALSE, vo.getDiff().getMatched());
        assertEquals("PERSON", vo.getDiff().getByType().keySet().iterator().next(), "差异类型必须可读");
        assertNonNullReason(vo.getReason());
    }

    @Test
    @DisplayName("GET /status PG_ONLY ⇒ diff.matched 为 null（未知 ≠ 不一致），原因非空")
    void statusPgOnlyLeavesDiffUnknown() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("graphForm", "PG_ONLY");
        map.put("lastSyncTime", null);
        map.put("reason", "当前档位无 Neo4j 投影（standard 档），PG 表为权威形态；Neo4j 为从属投影副本，可重放。");
        Map<String, Object> unreadable = new LinkedHashMap<>();
        unreadable.put("nodesPg", 5L);
        unreadable.put("nodesNeo4j", null);
        unreadable.put("nodesMatched", null);
        unreadable.put("edgesPg", 3L);
        unreadable.put("edgesNeo4j", null);
        unreadable.put("edgesMatched", null);
        unreadable.put("edgeSkipped", null);
        unreadable.put("byType", null);
        unreadable.put("matched", null);
        map.put("diff", unreadable);
        when(graphService.graphFormStatus()).thenReturn(map);

        GraphSyncStatusVO vo = controller().getGraphSyncStatus().getBody().getData();
        assertEquals("PG_ONLY", vo.getGraphForm());
        assertNull(vo.getLastSyncTime(), "无成功行即 null（不得编造时间）");
        assertNull(vo.getDiff().getMatched(), "读数不可得 ⇒ matched=null，禁写成 false 冒充不一致");
        assertNull(vo.getDiff().getNodesNeo4j());
        assertEquals(5L, vo.getDiff().getNodesPg(), "PG 权威数仍必须给出");
        assertNonNullReason(vo.getReason());
    }

    @Test
    @DisplayName("GET /status 台账/PG 读故障 ⇒ 原样上浮（由 advice 落 500 ECOS-KB-050）")
    void statusReadFailurePropagates() {
        DataAccessResourceFailureException boom =
                new DataAccessResourceFailureException("ecos_kg_sync_log unavailable");
        when(graphService.graphFormStatus()).thenThrow(boom);

        assertThrows(DataAccessResourceFailureException.class, () -> controller().getGraphSyncStatus());
    }

    // ── ④ /logs 分页 + traceId ───────────────────────────────────────────

    @Test
    @DisplayName("GET /logs ⇒ 200 {items,total,page,size}，条目含 traceId，分页参数原样下发")
    void logsCarryTraceIdAndPaging() {
        when(kgSyncService.getSyncLogsPage(2, 50)).thenReturn(logsPage());

        ResponseEntity<ApiResponse<GraphSyncLogListVO>> resp = controller().listGraphSyncLogs(2, 50);
        assertStatusAndCode(resp, 200, null);

        GraphSyncLogListVO vo = resp.getBody().getData();
        assertEquals(3L, vo.getTotal(), "total 必须取台账计数");
        assertEquals(2, vo.getPage(), "page 必须回显钳制后的实际值");
        assertEquals(50, vo.getSize());
        assertEquals(1, vo.getItems().size());
        assertEquals("trace-9", vo.getItems().get(0).getTraceId(), "条目必须含 traceId（附录 A 第 15 行）");
        assertEquals("success", vo.getItems().get(0).getStatus(), "status 已归一到前端词表");
    }

    @Test
    @DisplayName("GET /logs 台账不可读 ⇒ 原样上浮（禁返回空列表冒充「没有日志」）")
    void logsFailurePropagates() {
        DataAccessResourceFailureException boom =
                new DataAccessResourceFailureException("ecos_kg_sync_log unavailable");
        when(kgSyncService.getSyncLogsPage(1, 20)).thenThrow(boom);

        assertThrows(DataAccessResourceFailureException.class, () -> controller().listGraphSyncLogs(1, 20));
    }

    // ── ⑤ 反射钉：基址 + 三腿路径/动词 + 返回 ResponseEntity + 权限码 ─────

    @Test
    @DisplayName("反射钉：类基址 + 三腿方法与路径 + 返回 ResponseEntity + 权限码")
    void endpointSignaturesArePinned() throws Exception {
        assertEquals("/api/v1/knowledge/graph/sync",
                KnowledgeGraphSyncController.class.getAnnotation(RequestMapping.class).value()[0]);

        Method trigger = KnowledgeGraphSyncController.class.getMethod("triggerGraphSync", GraphSyncRequest.class);
        Method status = KnowledgeGraphSyncController.class.getMethod("getGraphSyncStatus");
        Method logs = KnowledgeGraphSyncController.class.getMethod("listGraphSyncLogs", int.class, int.class);

        assertEquals(0, trigger.getAnnotation(PostMapping.class).value().length,
                "POST 必须映射类基址本身（/api/v1/knowledge/graph/sync）");
        assertEquals("/status", status.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/logs", logs.getAnnotation(GetMapping.class).value()[0]);
        assertFalse(trigger.isAnnotationPresent(GetMapping.class), "触发腿是 POST");
        assertFalse(status.isAnnotationPresent(PostMapping.class), "形态状态是 GET 腿");

        for (Method m : List.of(trigger, status, logs)) {
            assertEquals(ResponseEntity.class, m.getReturnType(),
                    m.getName() + " 返回体退回 ApiResponse＝状态行将恒 200（D-B4-59 复现）");
            assertNotNull(m.getAnnotation(RequirePermission.class), m.getName() + " 缺权限声明");
        }
        assertEquals("knowledge:graph:sync", trigger.getAnnotation(RequirePermission.class).permission());
        assertEquals("knowledge:graph:read", status.getAnnotation(RequirePermission.class).permission());
        assertEquals("knowledge:graph:read", logs.getAnnotation(RequirePermission.class).permission());
    }

    // ── fixtures ────────────────────────────────────────────────────────

    private static Map<String, Object> pgOnly() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("graphForm", "PG_ONLY");
        m.put("attempted", 0);
        m.put("written", 0);
        m.put("notAttempted", 5);
        m.put("reason", "当前档位无 Neo4j 投影（standard 档），PG 表为权威形态；Neo4j 为从属投影副本，可重放。");
        m.put("reconcileDiff", null);
        m.put("jobId", "graph-sync-1");
        m.put("scope", "ALL");
        m.put("traceId", "trace-1");
        return m;
    }

    private static Map<String, Object> projected() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("graphForm", "PROJECTED");
        m.put("attempted", 2);
        m.put("written", 2L);
        m.put("notAttempted", 0);
        m.put("reason", null);
        m.put("reconcileDiff", matchedDiff());
        m.put("jobId", "graph-sync-2");
        m.put("scope", "ONTOLOGY");
        m.put("traceId", "trace-2");
        return m;
    }

    private static Map<String, Object> matchedDiff() {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("nodesPg", 2L);
        diff.put("nodesNeo4j", 2L);
        diff.put("nodesMatched", true);
        diff.put("edgesPg", 1L);
        diff.put("edgesNeo4j", 1L);
        diff.put("edgesMatched", true);
        diff.put("edgeSkipped", 0L);
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("pg", 2L);
        person.put("neo4j", 2L);
        person.put("matched", true);
        Map<String, Object> byType = new LinkedHashMap<>();
        byType.put("PERSON", person);
        diff.put("byType", byType);
        diff.put("matched", true);
        return diff;
    }

    private static Map<String, Object> mismatchDiff() {
        Map<String, Object> diff = matchedDiff();
        diff.put("nodesNeo4j", 1L);
        diff.put("nodesMatched", false);
        diff.put("matched", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> byType = (Map<String, Object>) diff.get("byType");
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("pg", 2L);
        person.put("neo4j", 1L);
        person.put("matched", false);
        byType.put("PERSON", person);
        return diff;
    }

    private static Map<String, Object> statusMap(String graphForm, String mismatchCode, Object diff) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("graphForm", graphForm);
        m.put("lastSyncTime", "2026-10-28T10:00:00.000+08:00");
        m.put("diff", diff);
        if (mismatchCode != null) {
            m.put("mismatchCode", mismatchCode);
            m.put("reason", "PG↔Neo4j 计数不一致：nodes pg=2/neo4j=1");
        }
        return m;
    }

    private static Map<String, Object> logsPage() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("jobId", "graph-sync-9");
        entry.put("objectType", "ALL");
        entry.put("operation", "NEO4J_SYNC");
        entry.put("status", "success");
        entry.put("progress", 100);
        entry.put("nodes", 2);
        entry.put("edges", 1);
        entry.put("message", "");
        entry.put("timestamp", "2026-10-28 10:00:00.0");
        entry.put("finishedAt", "2026-10-28 10:00:01.0");
        entry.put("traceId", "trace-9");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(entry);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("items", items);
        page.put("total", 3);
        page.put("page", 2);
        page.put("size", 50);
        return page;
    }

    private static void assertNonNullReason(String reason) {
        assertNotNull(reason, "形态说明必须给出原因");
        assertFalse(reason.isBlank(), "原因不得为空串");
    }
}
