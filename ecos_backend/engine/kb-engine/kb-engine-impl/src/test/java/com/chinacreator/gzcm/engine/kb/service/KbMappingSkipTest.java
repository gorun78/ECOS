package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO.IssueVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * F03-05 实体映射契约硬规则②：{@code materialized} 裁度语义固化（PRD-03 §3.2-2，Q_SKIP 口径；
 * 设计-03 W71/C55）。
 *
 * <p>裁度（F03-05 明列）：{@code null / "" / "true" / "1"} = true（继续实例化）；
 * 其他 = skip（记 {@code Q2_SKIP}，不实例化、不落库）。以<b>穷举</b>单测锁定两侧。
 *
 * <p>复用 {@code KbDryRunContractTest} 的纯 Mockito 骨架：本体快照/资源清单/字段/行走
 * JdbcTemplate + RestTemplate stub（零真实网络/DB）。materialized=false 分支在行拉取之前短路，
 * Q2_SKIP 判据完全由 stub 的映射 {@code materialized} 值驱动。
 */
class KbMappingSkipTest {

    private static final String DN = "http://dn";
    private static final String ONT = "http://ont/api/v1";
    private static final String ONT_ID = "O1";

    private JdbcTemplate jdbc;
    private RestTemplate restTemplate;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("materialized 裁度 true 侧：缺省/null、'true'、'1'、空串 → 实例化，不记 Q2_SKIP")
    void materializedTrueSide() {
        // "--" = 缺省键（null 语义）；"" / "true" / "1" = 显式 true 侧
        for (String raw : new String[]{"--", "", "true", "1"}) {
            EntityInstanceExtractionReportVO r = runExtraction(raw);
            assertTrue(!hasIssue(r, "Q2_SKIP", "EMP"),
                    "materialized=" + raw + " 应视为 true，不得记 Q2_SKIP");
            assertTrue(r.getNodeCreated() > 0,
                    "materialized=" + raw + " 应参与实例化（>=1 新建节点）");
        }
    }

    @Test
    @DisplayName("materialized 裁度 skip 侧：'false'/'0'/任意串 → Q2_SKIP 且零落库")
    void materializedSkipSide() {
        for (String v : new String[]{"false", "0", "nope"}) {
            EntityInstanceExtractionReportVO r = runExtraction(v);
            assertTrue(hasIssue(r, "Q2_SKIP", "EMP"),
                    "materialized=" + v + " 应记 Q2_SKIP");
            assertEquals(0, r.getNodeCreated(), "被 skip 实体不得新建节点");
            assertEquals(0, r.getNodeUpdated(), "被 skip 实体不得更新节点");
        }
    }

    @Test
    @DisplayName("契约字段集只增不改（F03-05 D.2）：entityCode/datasetId/resourceName/materialized/fieldMappings{source,target} 齐备")
    void contractFieldSetCarried() {
        // 白线：抽取链路确实消费这些契约字段（缺任一 → 契约面漂移应在此/冻结测试暴露）
        EntityInstanceExtractionReportVO r = runExtraction("true");
        assertTrue(!hasIssue(r, "Q2_SKIP", "EMP"), "materialized='true' 实体应进入抽取");
        // 若 fieldMappings 缺 source/target，C4 主键解析会 INVALID_MAPPING；此处断言未触发即契约字段在位
        assertTrue(r.getInvalidMappings() == 0,
                "契约字段在位时 C4 不应判 INVALID_MAPPING");
    }

    // ── 驱动 ──

    /**
     * 跑一次抽取，返回报告。
     *
     * @param raw 映射 materialized stub 值；"--" 表示键缺省（测试 null 语义）
     */
    private EntityInstanceExtractionReportVO runExtraction(String raw) {
        jdbc = Mockito.mock(JdbcTemplate.class);
        restTemplate = Mockito.mock(RestTemplate.class);
        KbEntityInstanceExtractionService service =
                new KbEntityInstanceExtractionService(jdbc, restTemplate, DN, ONT, true);

        // 本体快照：entity_codes 含 EMP → C1 通过
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("ontology_id", ONT_ID);
        snap.put("version", "v1");
        snap.put("entity_codes", "[\"EMP\"]");
        when(jdbc.queryForList(
                argThat((String s) -> s != null && s.contains("kb_ontology_snapshot")),
                eq(ONT_ID))).thenReturn(List.of(snap));

        // 资源清单：res1=EMP
        when(restTemplate.getForObject(DN + "/api/v1/engine/data/layers/CURATED", Map.class))
            .thenReturn(body(Map.of("resources", List.of(
                    Map.of("resource_id", "res1", "resource_name", "EMP")))));

        // 映射契约：materialized 依 raw 注入（"--" = 缺省键，"" = 空串）
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("entityCode", "EMP");
        mapping.put("datasetId", "res1");
        mapping.put("sourceType", "hr");
        mapping.put("sourceName", "员工");
        mapping.put("fieldMappings", List.of(Map.of("source", "id", "target", "emp_pk")));
        if (!"--".equals(raw)) {
            mapping.put("materialized", raw);
        }
        when(restTemplate.getForObject(ONT + "/ontology/entity-mappings?ontologyId=" + ONT_ID, Map.class))
            .thenReturn(body(List.of(mapping)));

        // 实体/关系清单：空 → nodeType 回退、无 C2 边
        when(restTemplate.getForObject(ONT + "/ecos/ontologies/" + ONT_ID + "/entities", Map.class))
            .thenReturn(body(Collections.emptyList()));
        when(restTemplate.getForObject(ONT + "/ecos/ontologies/" + ONT_ID + "/relationships", Map.class))
            .thenReturn(body(Collections.emptyList()));

        // 字段元数据：id 主键（true 侧消费；skip 侧在到达这里前已短路，未命中 stub 返回 null 亦无碍）
        when(restTemplate.getForObject(DN + "/api/v1/datanet/metadata/fields/res1", Map.class))
            .thenReturn(body(List.of(Map.of("fieldName", "id", "primaryKey", "true"))));

        // 实例行：1 行全新主键
        when(restTemplate.getForObject(
                DN + "/api/v1/engine/data/layers/CURATED/resources/res1/rows?limit=500", Map.class))
            .thenReturn(body(rowPage(List.of(Map.of("id", "e1")))));

        // 真执行落库 stub（校验调用签名，见 KbDryRunContractTest）
        when(jdbc.queryForList(
                argThat((String s) -> s != null && s.contains("INSERT INTO ecos_knowledge.graph_node")),
                eq(Boolean.class),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(Boolean.TRUE));

        return service.extract(ONT_ID, "job-mat", false, false);
    }

    private static boolean hasIssue(EntityInstanceExtractionReportVO r, String code, String entity) {
        for (IssueVO i : r.getIssues()) {
            if (code.equals(i.getCode()) && entity.equals(i.getEntityCode())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> body(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", data);
        return m;
    }

    private static Map<String, Object> rowPage(List<Map<String, Object>> rows) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("rows", rows);
        page.put("hasMore", false);
        page.put("nextWatermark", "wm2");
        return page;
    }
}
