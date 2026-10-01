package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dao.KbEntityInstanceExtractionServiceDao;
import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F04-09 dry-run 真口径守护 — 验收测试（详细设计-04 二·F04-09，REQ-KB-05，守护型）。
 *
 * <p>契约：同一输入下 dry-run 与真执行的 {@code {created,updated,issues[]}} 三项 diff=0；
 * dry-run 不改水位线、不改表行数。</p>
 *
 * <p>纯单测：RestTemplate/DAO 全 mock，注入受控 DW 数据（1 实体 × 3 行，distinct 主键）。
 * dry-run 走"预查存在性集合判 created/updated"，真执行走 upsert 返回的 inserted 标志——两条路径
 * 必须收敛到同计数（A 新建 / B、C 更新 / 0 告警），防止 nodeCreated 虚高误导。</p>
 *
 * <p>R2 / B2-kb: 数据访问已由 {@link JdbcTemplate} 收口到
 * {@link KbEntityInstanceExtractionServiceDao}，本测试 mock DAO 验证契约。</p>
 */
class KbDryRunContractTest {

    private static final Map<String, Object> CURATED_RESOURCES = Map.of(
            "data", Map.of("resources", List.of(
                    Map.of("resource_id", "rsrc1", "resource_name", "person"))));

    private static final Map<String, Object> MAPPINGS = Map.of(
            "entityCode", "PERSON",
            "datasetId", "rsrc1",
            "resourceName", "person",
            "sourceType", "hr",
            "sourceName", "人员",
            "fieldMappings", List.of(Map.of("source", "id", "target", "code")));

    private static final Map<String, Object> FIELDS = Map.of(
            "data", List.of(
                    Map.of("fieldName", "id", "primaryKey", "true"),
                    Map.of("fieldName", "code")));

    private static final List<Map<String, Object>> ROWS = List.of(
            Map.of("id", "A", "code", "p-a"),
            Map.of("id", "B", "code", "p-b"),
            Map.of("id", "C", "code", "p-c"));

    private static final String DATANET = "http://datanet.test";

    // ── dry-run 与真执行 {created,updated,issues} diff=0 ──────────
    @Test
    void dryRunMatchesRealExecutionOnCreatedUpdatedIssues() {
        // 场景：三行实例全部已存在（存在性回显全部 nodeId 为已有 / upsert 全返回 updated）。
        // dry-run（存在性预查）与真执行（upsert inserted 标志）必须收敛到同计数 → 0 建 3 更。
        EntityInstanceExtractionReportVO dry = extractWith(true, true);
        EntityInstanceExtractionReportVO real = extractWith(false, true);

        assertEquals(real.getNodeCreated(), dry.getNodeCreated(), "created 计数 dry-run 与真执行一致");
        assertEquals(real.getNodeUpdated(), dry.getNodeUpdated(), "updated 计数 dry-run 与真执行一致");
        assertEquals(real.getIssues().size(), dry.getIssues().size(), "issues 条目数一致（diff=0）");
        // 全部已存在语义锚定：0 新建 + 全部是更新（dry-run 与真执行的 rowsRead 也要一致）
        assertEquals(0, dry.getNodeCreated(), "0 条新建（三行实体均已存在）");
        assertEquals(0, real.getNodeCreated(), "真执行同口径 0 新建");
        assertTrue(dry.getNodeUpdated() >= 3,
                "dry-run 应计至少 3 条更新（三行实体各一），实际=" + dry.getNodeUpdated());
        assertEquals(dry.getNodeUpdated(), real.getNodeUpdated(), "两路径 updated 同值");
        assertEquals(dry.getRowsRead(), real.getRowsRead(), "两路径 rowsRead 同值（同输入下 diff=0）");
        assertTrue(real.getIssues().isEmpty(), "无 C1~C4 告警（映射/字段/属性均合法）");
        assertTrue(dry.isDryRun());
    }

    // ── dry-run 不改水位线、不改表行数 ──────────────────────────
    @Test
    void dryRunLeavesWatermarkAndRowCountUnchanged() {
        KbEntityInstanceExtractionServiceDao dao = mock(KbEntityInstanceExtractionServiceDao.class);
        stubSnapshots(dao);
        AtomicInteger upsertCalls = new AtomicInteger();
        lenient().when(dao.upsertNode(anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    upsertCalls.incrementAndGet();
                    return true;
                });
        // 存在性预查 → 返回 {B,C}
        lenient().when(dao.queryExistingNodeIds(anyList())).thenReturn(List.of("B", "C"));

        KbEntityInstanceExtractionService svc = newSrv(dao, restRoutes("wm-3"));
        EntityInstanceExtractionReportVO dry = svc.extract("ONT", "job-dry", false, true);
        assertTrue(dry.isDryRun());

        // dry-run 禁止节点 upsert（graph_node 零写入 → 表行数不变）
        assertEquals(0, upsertCalls.get(), "dry-run 不得执行节点 upsert（graph_node 零写入）");
        // 禁止边写 / 水位写
        verify(dao, Mockito.never()).upsertEdge(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any());
        verify(dao, Mockito.never()).writeWatermark(anyString(), anyString(), anyString(), anyString());
        // 水位线不推进（dry-run 不落持久化水位）
        assertFalse(dry.getNextWatermark() != null && !dry.getNextWatermark().isBlank(),
                "dry-run 报告不得回填持久化水位（不落库即不推进）");
    }

    // ── stubs & helpers ─────────────────────────────────────────

    /** 场景开关 {@code allExisting=true}：存在性全命中 + upsert 全返回更新（0 建 3 更）。 */
    private EntityInstanceExtractionReportVO extractWith(boolean dryRun, boolean allExisting) {
        KbEntityInstanceExtractionServiceDao dao = mock(KbEntityInstanceExtractionServiceDao.class);
        stubSnapshots(dao);
        // upsert：真执行命中，按 allExisting 返回 inserted（true=新建 / false=更新）
        lenient().when(dao.upsertNode(anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(!allExisting);
        // 存在性预查：全命中（三行实体节点均已存在）或全空
        lenient().when(dao.queryExistingNodeIds(anyList()))
                .thenAnswer(inv -> allExisting
                        ? List.of(nodeId("A"), nodeId("B"), nodeId("C"))
                        : List.of());

        KbEntityInstanceExtractionService svc = newSrv(dao, restRoutes("wm-3"));
        return svc.extract("ONT", "job-" + (dryRun ? "dry" : "real"), false, dryRun);
    }

    private KbEntityInstanceExtractionService newSrv(KbEntityInstanceExtractionServiceDao dao, RestTemplate rest) {
        KbEntityInstanceExtractionService svc =
                new KbEntityInstanceExtractionService(dao, rest, DATANET, true);
        ServiceEndpointResolver resolver = mock(ServiceEndpointResolver.class);
        when(resolver.resolve(anyString(), any()))
                .thenReturn(Optional.of("http://buszhi.test/api/v1"));
        ReflectionTestUtils.setField(svc, "endpointResolver", resolver);
        return svc;
    }

    /** 与主服务 buildNodeId 同口径：sha256(ONT::PERSON::pk) 前 32 hex */
    private static String nodeId(String pk) {
        byte[] bytes = sha256("ONT::PERSON::" + pk);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.substring(0, 32);
    }

    private static byte[] sha256(String input) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private RestTemplate restRoutes(String nextWm) {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.getForObject(anyString(), ArgumentMatchers.eq(Map.class)))
                .thenAnswer(inv -> routes((String) inv.getArgument(0), nextWm));
        return rest;
    }

    private void stubSnapshots(KbEntityInstanceExtractionServiceDao dao) {
        // scope=ONT（非 ALL）→ selectLatestSnapshot；提供快照行供 C1 校验
        lenient().when(dao.selectLatestSnapshot(anyString())).thenReturn(List.of(
                Map.of("ontology_id", "ONT", "version", "1", "entity_codes", "[\"PERSON\"]")));
        lenient().when(dao.selectAllSnapshots()).thenReturn(List.of(
                Map.of("ontology_id", "ONT", "version", "1", "entity_codes", "[\"PERSON\"]")));
    }

    private Map<String, Object> routes(String url, String nextWm) {
        if (url.contains("/layers/") && !url.contains("/resources/")) {
            return CURATED_RESOURCES;
        }
        if (url.contains("/entity-mappings")) {
            return Map.of("data", List.of(MAPPINGS));
        }
        if (url.contains("/properties")) {
            return Map.of("data", List.of());
        }
        if (url.contains("/ecos/ontologies") && url.contains("/entities")) {
            return Map.of("data", List.of(Map.of("id", "PERSON", "code", "PERSON")));
        }
        if (url.contains("/metadata/fields")) {
            return FIELDS;
        }
        if (url.contains("/rows")) {
            return Map.of("data", Map.of("rows", ROWS, "hasMore", false, "nextWatermark", nextWm));
        }
        return Map.of("data", List.of()); // properties / relationships
    }
}
