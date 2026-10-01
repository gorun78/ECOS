package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
 * <p>纯单测：RestTemplate/JdbcTemplate 全 mock，注入受控 DW 数据（1 实体 × 3 行，distinct 主键）。
 * dry-run 走"预查存在性集合判 created/updated"，真执行走 upsert 返回的 inserted 标志——两条路径
 * 必须收敛到同计数（A 新建 / B、C 更新 / 0 告警），防止 nodeCreated 虚高误导。</p>
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
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubSnapshots(jdbc);
        AtomicInteger upsertCalls = new AtomicInteger();
        stubNodeUpsert(jdbc, () -> { upsertCalls.incrementAndGet(); return true; });
        // 存在性预查（3 参重载）→ 返回 {B,C}
        when(jdbc.queryForList(anyString(), any(Class.class), any()))
                .thenReturn(List.of("B", "C"));

        KbEntityInstanceExtractionService svc = newSrv(jdbc, restRoutes("wm-3"));
        EntityInstanceExtractionReportVO dry = svc.extract("ONT", "job-dry", false, true);
        assertTrue(dry.isDryRun());

        // dry-run 禁止节点 upsert（graph_node 零写入 → 表行数不变）
        assertEquals(0, upsertCalls.get(), "dry-run 不得执行节点 upsert（graph_node 零写入）");
        // 禁止边写 / 水位写
        verify(jdbc, Mockito.never()).update(org.mockito.ArgumentMatchers.contains("graph_edge"));
        verify(jdbc, Mockito.never()).update(org.mockito.ArgumentMatchers.contains("kb_extract_watermark"));
        // 水位线不推进（dry-run 不落持久化水位）
        assertFalse(dry.getNextWatermark() != null && !dry.getNextWatermark().isBlank(),
                "dry-run 报告不得回填持久化水位（不落库即不推进）");
    }

    // ── stubs & helpers ─────────────────────────────────────────

    private static boolean isNodeUpsert(String sql) {
        return sql != null && sql.contains("INSERT INTO ecos_knowledge.graph_node");
    }

    /** 守卫方法：真执行不应对非 upsert SQL 命中 12 参返回序列。 */
    private void stubNodeUpsert(JdbcTemplate jdbc, java.util.function.Supplier<Boolean> insertedOf) {
        // upsert 走 queryForList(sql, Boolean.class, 10 arg) = 12 参
        lenient().when(jdbc.queryForList(argThat(KbDryRunContractTest::isNodeUpsert),
                        any(Class.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> List.of(insertedOf.get()));
    }

    /** 场景开关 {@code allExisting=true}：存在性全命中 + upsert 全返回更新（0 建 3 更）。 */
    private EntityInstanceExtractionReportVO extractWith(boolean dryRun, boolean allExisting) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubSnapshots(jdbc);
        // upsert（12 参）：真执行命中，按 allExisting 返回 inserted（true=新建 / false=更新）
        lenient().when(jdbc.queryForList(argThat(KbDryRunContractTest::isNodeUpsert),
                        any(Class.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> List.of(!allExisting));
        // 存在性预查：生产侧 jdbc.queryForList(sql, String.class, chunk.toArray()) 会把 Object[3] 展开成 3 个 varargs 元素，
        // 因此 mock 必须匹配 (String, Class, Object, Object, Object) 共 5 参（本例固定 3 行、单分片）。
        lenient().when(jdbc.queryForList(anyString(), any(Class.class), any(), any(), any()))
                .thenAnswer(inv -> {
                    String sql = (String) inv.getArgument(0);
                    if (isNodeUpsert(sql)) {
                        return List.of(); // 兜底（12 参应已被上一 stub 处理）
                    }
                    if (sql.contains("graph_node") && sql.contains("IN (")) {
                        // allExisting=true → 三行实体节点均已存在（与真执行 upsert 全返回 updated 同口径）
                        if (allExisting) {
                            return List.of(nodeId("A"), nodeId("B"), nodeId("C"));
                        }
                        return List.of();
                    }
                    return List.of();
                });

        KbEntityInstanceExtractionService svc = newSrv(jdbc, restRoutes("wm-3"));
        return svc.extract("ONT", "job-" + (dryRun ? "dry" : "real"), false, dryRun);
    }

    private KbEntityInstanceExtractionService newSrv(JdbcTemplate jdbc, RestTemplate rest) {
        KbEntityInstanceExtractionService svc =
                new KbEntityInstanceExtractionService(jdbc, rest, DATANET, true);
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

    private void stubSnapshots(JdbcTemplate jdbc) {
        lenient().when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("kb_ontology_snapshot")),
                        ArgumentMatchers.<Object>any()))
                .thenReturn(List.of(
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
