package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * F04-09 验收（mvn -Dtest=KbDryRunContractTest）— dry-run 真口径守护（REQ-KB-05 P1，K-50 纠正）。
 *
 * <p>契约（设计 I-1 / I-2 / F04-09）：
 * <ul>
 *   <li>{@code dryRunMatchesRealExecutionOnCreatedUpdatedIssues}：
 *       同一输入 dry-run 与真执行的 {@code {created,updated,issues[]}} 三项 diff = 0；</li>
 *   <li>{@code dryRunLeavesWatermarkAndRowCountUnchanged}：
 *       dry-run 不改水位线（kb_extract_watermark）、不写实例表（graph_node 行数 0 增）。</li>
 * </ul>
 *
 * <p>纯 Mockito：快照/水位/节点写走 {@link JdbcTemplate} mock；DW 行/字段/映射走
 * {@link RestTemplate} {@code getForObject} mock（零真实网络）。不触 DB/网络。
 *
 * @author ECOS KB Team
 */
class KbDryRunContractTest {

    private JdbcTemplate jdbc;
    private RestTemplate restTemplate;
    private KbEntityInstanceExtractionService service;

    private static final String DN = "http://dn";
    private static final String ONT = "http://ont/api/v1";
    private static final String ONT_ID = "O1";

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        restTemplate = Mockito.mock(RestTemplate.class);
        service = new KbEntityInstanceExtractionService(jdbc, restTemplate, DN, ONT, true);

        // ── 本体快照（JDBC，单 scope=O1）：entity_codes 含 EMP → C1 通过；两跑共用 ──
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("ontology_id", ONT_ID);
        snap.put("version", "v1");
        snap.put("entity_codes", "[\"EMP\"]");
        when(jdbc.queryForList(
                argThat((String s) -> s != null && s.contains("kb_ontology_snapshot")),
                eq(ONT_ID)))
            .thenReturn(List.of(snap));

        // ── DW 层资源清单（REST）：resource_id=res1 ──
        when(restTemplate.getForObject(DN + "/api/v1/engine/data/layers/CURATED", Map.class))
            .thenReturn(body(Map.of("resources", List.of(
                    Map.of("resource_id", "res1", "resource_name", "EMP")))));

        // ── 映射契约（REST）：entityCode=EMP，datasetId=res1，2 字段，无关系引用 → 无边 ──
        Map<String, Object> fm1 = Map.of("source", "id", "target", "emp_pk");
        Map<String, Object> fm2 = Map.of("source", "name", "target", "emp_name");
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("entityCode", "EMP");
        mapping.put("datasetId", "res1");
        mapping.put("sourceType", "hr");
        mapping.put("sourceName", "员工实例");
        mapping.put("fieldMappings", List.of(fm1, fm2));
        when(restTemplate.getForObject(ONT + "/ontology/entity-mappings?ontologyId=" + ONT_ID, Map.class))
            .thenReturn(body(List.of(mapping)));

        // ── 实体清单（REST）：空 → nodeType 回退 entityCode ──
        when(restTemplate.getForObject(ONT + "/ecos/ontologies/" + ONT_ID + "/entities", Map.class))
            .thenReturn(body(Collections.emptyList()));
        // ── 关系定义（REST）：空 → 无 C2 候选边 ──
        when(restTemplate.getForObject(ONT + "/ecos/ontologies/" + ONT_ID + "/relationships", Map.class))
            .thenReturn(body(Collections.emptyList()));

        // ── 字段元数据（REST）：id 主键 / name 普通列 ──
        when(restTemplate.getForObject(DN + "/api/v1/datanet/metadata/fields/res1", Map.class))
            .thenReturn(body(List.of(
                    Map.of("fieldName", "id", "primaryKey", "true"),
                    Map.of("fieldName", "name"))));

        // ── DW 实例行（REST）：2 行全新主键 e1/e2，命中下一页水位 wm2，末页 ──
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("id", "e1", "name", "A"));
        rows.add(Map.of("id", "e2", "name", "B"));
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("rows", rows);
        page.put("hasMore", false);
        page.put("nextWatermark", "wm2");
        when(restTemplate.getForObject(
                DN + "/api/v1/engine/data/layers/CURATED/resources/res1/rows?limit=500", Map.class))
            .thenReturn(body(page));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Map<String, Object> body(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", data);
        return m;
    }

    @Test
    @DisplayName("F04-09: 同一输入 dry-run 与真执行 {created,updated,issues[]} 三项一致（diff=0）")
    void dryRunMatchesRealExecutionOnCreatedUpdatedIssues() {
        // 真执行：2 全新行 → upsertNode 返回 inserted=true ×2 → nodeCreated=2
        // upsertNode 走 jdbc.queryForList(sql, Boolean.class, 10 个 varargs)
        var insertMatcher = argThat((String s) -> s != null && s.contains("INSERT INTO ecos_knowledge.graph_node"));
        when(jdbc.queryForList(
                insertMatcher,
                eq(Boolean.class),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()))
            .thenReturn(List.of(Boolean.TRUE));

        // dry-run：存在性预查 2 个 nodeId 全未命中 → nodeCreated=2
        // queryExistingNodeIds 走 jdbc.queryForList(sql, String.class, chunk.toArray())
        var probeMatcher = argThat((String s) -> s != null && s.contains("SELECT id FROM ecos_knowledge.graph_node"));
        when(jdbc.queryForList(
                probeMatcher,
                eq(String.class),
                (Object[]) any()))
            .thenReturn(Collections.emptyList());

        EntityInstanceExtractionReportVO dry = service.extract(ONT_ID, "job-dry", false, true);
        EntityInstanceExtractionReportVO real = service.extract(ONT_ID, "job-real", false, false);

        // 三项 diff = 0
        assertEquals(real.getNodeCreated(), dry.getNodeCreated(), "created 口径一致");
        assertEquals(real.getNodeUpdated(), dry.getNodeUpdated(), "updated 口径一致");
        assertEquals(real.getIssues(), dry.getIssues(), "issues 口径一致");

        // 且真值确为契约预期（2 行全新 → created=2 / updated=0 / issues 空）
        assertEquals(2, dry.getNodeCreated(), "同 2 全新行 dry-run 判我均新建");
        assertEquals(0, dry.getNodeUpdated());
        assertTrue(dry.getIssues().isEmpty(), "无 C1/C2/C3 违规 → issues 空");
        assertTrue(real.getIssues().isEmpty());
    }

    @Test
    @DisplayName("F04-09: dry-run 不改水位线、不写实例行（rowCount 0 增）")
    void dryRunLeavesWatermarkAndRowCountUnchanged() {
        // dry-run 存在性预查命中判定为"新建"
        when(jdbc.queryForList(
                argThat((String s) -> s != null && s.contains("SELECT id FROM ecos_knowledge.graph_node")),
                eq(String.class),
                (Object[]) any()))
            .thenReturn(Collections.emptyList());

        EntityInstanceExtractionReportVO dry = service.extract(ONT_ID, "job-dry2", false, true);

        assertTrue(dry.isDryRun());
        assertEquals(2, dry.getNodeCreated(), "dry-run 仅出统计，不落库");

        // 逐检 JDBC 调用：既不得写水位线，也不得 upsert 实例节点
        boolean touchedWatermark = false;
        boolean touchedGraphNodeInsert = false;
        for (org.mockito.invocation.Invocation inv : Mockito.mockingDetails(jdbc).getInvocations()) {
            Object a0 = inv.getArguments().length > 0 ? inv.getArguments()[0] : null;
            if (a0 instanceof String sql) {
                if (sql.contains("kb_extract_watermark")) {
                    touchedWatermark = true;
                }
                if (sql.contains("INSERT INTO ecos_knowledge.graph_node")) {
                    touchedGraphNodeInsert = true;
                }
            }
        }
        assertFalse(touchedWatermark, "dry-run 禁触 kb_extract_watermark（禁读/写水位线）");
        assertFalse(touchedGraphNodeInsert, "dry-run 禁 upsert graph_node（禁增实例行）");
    }
}
