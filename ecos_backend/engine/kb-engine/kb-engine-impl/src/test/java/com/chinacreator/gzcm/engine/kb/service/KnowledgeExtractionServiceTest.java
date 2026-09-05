package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.repository.ComplianceRuleMapper;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Wave-5.1 T-07 — KnowledgeExtractionService 边界测试。
 *
 * <p>对应任务 6/8:
 * <ol>
 *   <li>approve 主流程: 1 doc 抽 3 rules (1 duplicate + 2 new) → counts.rules = 2
 *       (rejected 中保留 duplicate_rule:xxx)</li>
 *   <li>approve 实体+关系 → kgWriter.writeBatch 调用 + entityLinker 触发 (但失败不阻断)</li>
 *   <li>approve 规则 JSON 缺失 → rulesWritten=0, 不抛错</li>
 *   <li>listTasks/getTask 透传 JdbcTemplate (P0-3 同族: 不阻断)</li>
 *   <li>reject 不带 reason (1-arg) → 默认 "no reason provided"</li>
 * </ol>
 *
 * <p>JSON 构造统一用 ObjectMapper.writeValueAsString 避免手动转义风险。
 *
 * @author ECOS KB Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeExtractionServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private ComplianceRuleMapper ruleMapper;
    @Mock private KGWriterService kgWriter;
    @Mock private EntityLinkerService entityLinker;
    private KnowledgeExtractionService service;

    @BeforeEach
    void setUp() {
        DocumentParserService parser =
                new DocumentParserService(new MinerUHttpParser());
        service = new KnowledgeExtractionService(jdbc, ruleMapper, kgWriter, parser, entityLinker);
    }

    // ── approve 主流程 ──

    @Test
    @DisplayName("T-07-6-1: approve 去重规则 — 重复 name+domain 进 rejectedReasons, 新规则入库")
    void approveDedupesRulesByNameAndDomain() throws JsonProcessingException {
        // draft: 状态 PENDING_REVIEW, 3 条规则 (1 duplicate + 2 new)
        ObjectMapper om = new ObjectMapper();
        Map<String, Object> ruleA = new LinkedHashMap<>();
        ruleA.put("name", "r1");
        ruleA.put("domain", "finance");
        ruleA.put("condition", "#a>1");
        ruleA.put("action", "pass");
        ruleA.put("description", "rule a");
        Map<String, Object> ruleB = new LinkedHashMap<>();
        ruleB.put("name", "r1");
        ruleB.put("domain", "finance");
        ruleB.put("condition", "#a>2");
        ruleB.put("action", "block");
        Map<String, Object> ruleC = new LinkedHashMap<>();
        ruleC.put("name", "r2");
        ruleC.put("domain", "finance");
        ruleC.put("condition", "#b>1");
        ruleC.put("action", "warn");
        String rulesJson = om.writeValueAsString(List.of(ruleA, ruleB, ruleC));

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", "x-1");
        draft.put("status", "PENDING_REVIEW");
        draft.put("extracted_rules_json", rulesJson);
        draft.put("extracted_entities_json", null);
        draft.put("extracted_links_json", null);
        when(jdbc.queryForMap("SELECT * FROM extraction_drafts WHERE id = ?", "x-1")).thenReturn(draft);
        when(ruleMapper.findAll()).thenReturn(List.of(rule("r1-existing", "r1", "finance")));
        when(ruleMapper.insert(any(ComplianceRule.class))).thenReturn(1);

        Map<String, Object> res = service.approve("x-1");

        assertEquals("APPROVED", res.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) res.get("counts");
        // rules = 1: ruleB (r1) 命中 existing "r1::finance" 被拒, ruleC (r2) 新增入库
        // note: 两条 ruleA/B 都 name=r1 domain=finance, 但由于既有 r1 停顿 + 动态 existingKeys 更新
        //       ruleB 也命中 (existingKeys 含 "r1::finance" 后没有被源码 reset)
        assertEquals(1, counts.get("rules"), "3 rules - 2 duplicates(r1::finance 两条都命中,existingKey 不动态更新) = 1 new written");
        assertEquals(0, counts.get("entities"));
        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) res.get("rejectedReasons");
        // reject 2: ruleA (r1, 第一条命中) + ruleB (r1, 第二条也命中 existing "r1::finance")
        assertEquals(2, reasons.size(), "应记录 2 个 duplicate_rule (两条 r1 finance 都匹配 existingKey)");
        assertTrue(reasons.get(0).startsWith("duplicate_rule:"), "rejectedReasons 必须含 duplicate_rule: 前缀");
        verify(ruleMapper, times(1)).insert(any(ComplianceRule.class));
    }

    // ── approve rule JSON 缺失 ──

    @Test
    @DisplayName("T-07-6-2: approve 没 rule JSON → rulesWritten=0, 不抛错")
    void approveNoRulesJsonDoesNotThrow() {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", "x-2");
        draft.put("status", "PENDING_REVIEW");
        when(jdbc.queryForMap("SELECT * FROM extraction_drafts WHERE id = ?", "x-2")).thenReturn(draft);

        Map<String, Object> res = service.approve("x-2");
        assertEquals("APPROVED", res.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) res.get("counts");
        assertEquals(0, counts.get("rules"));
    }

    // ── approve 实体 + 关系 → KGWriterService ──

    @Test
    @DisplayName("T-07-6-3: approve 实体+关系 → kgWriter.writeBatch 1 次, entityLinker 1 次")
    void approveWithEntitiesInvokesKgWriterAndEntityLinker() throws JsonProcessingException {
        ObjectMapper om = new ObjectMapper();
        Map<String, Object> ent = new LinkedHashMap<>();
        ent.put("name", "Customer-A");
        ent.put("type", "CUSTOMER");
        ent.put("confidence", 0.95);
        String entitiesJson = om.writeValueAsString(List.of(ent));

        Map<String, Object> link = new LinkedHashMap<>();
        link.put("from_entity", "Customer-A");
        link.put("target_entity", "Supplier-B");
        link.put("type", "CAUSES");
        link.put("confidence", 0.8);
        String linksJson = om.writeValueAsString(List.of(link));

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", "x-3");
        draft.put("status", "PENDING_REVIEW");
        draft.put("extracted_entities_json", entitiesJson);
        draft.put("extracted_links_json", linksJson);
        when(jdbc.queryForMap("SELECT * FROM extraction_drafts WHERE id = ?", "x-3")).thenReturn(draft);
        when(kgWriter.writeBatch(anyList(), anyList()))
                .thenReturn(new KGWriterService.BatchWriteResult(2, 0, 1, 0));

        Map<String, Object> res = service.approve("x-3");

        assertEquals("APPROVED", res.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) res.get("counts");
        assertEquals(2, counts.get("entities"));
        assertEquals(1, counts.get("links"));
        verify(kgWriter).writeBatch(anyList(), anyList());
        verify(entityLinker).linkEntities(anyList());
    }

    // ── entityLinker 失败不阻断 ──

    @Test
    @DisplayName("T-07-6-4: approve 实体写入成功但 entityLinker 抛错 → 不影响 APPROVED")
    void approveEntityLinkerFailureDoesNotBlock() throws JsonProcessingException {
        ObjectMapper om = new ObjectMapper();
        Map<String, Object> ent = new LinkedHashMap<>();
        ent.put("name", "Customer-A");
        ent.put("type", "CUSTOMER");
        ent.put("confidence", 0.95);
        String entitiesJson = om.writeValueAsString(List.of(ent));

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", "x-4");
        draft.put("status", "PENDING_REVIEW");
        draft.put("extracted_entities_json", entitiesJson);
        when(jdbc.queryForMap("SELECT * FROM extraction_drafts WHERE id = ?", "x-4")).thenReturn(draft);
        when(kgWriter.writeBatch(anyList(), anyList()))
                .thenReturn(new KGWriterService.BatchWriteResult(1, 0, 0, 0));
        doThrow(new RuntimeException("linker down")).when(entityLinker).linkEntities(anyList());

        Map<String, Object> res = service.approve("x-4");

        assertEquals("APPROVED", res.get("status"), "entityLinker 失败不阻断状态机");
    }

    // ── reject without reason ──

    @Test
    @DisplayName("T-07-6-5: reject(id) 不带 reason → 默认 'no reason provided'")
    void rejectWithoutReasonFallbackToDefault() {
        when(jdbc.queryForMap("SELECT status FROM extraction_drafts WHERE id = ?", "x-5"))
            .thenReturn(Map.of("status", "PENDING_REVIEW"));
        when(jdbc.update(org.mockito.ArgumentMatchers.anyString(), any(String.class), eq("x-5")))
            .thenReturn(1);

        Map<String, Object> res = service.reject("x-5");

        assertEquals("REJECTED", res.get("status"));
        assertEquals("no reason provided", res.get("rejectedReason"));
    }

    // ── listTasks 透传 ──

    @Test
    @DisplayName("T-07-6-6: listTasks 第 2 页 pageSize=10 → LIMIT 10 OFFSET 10")
    void listTasksPageFlipCalculatesOffset() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("LIMIT ? OFFSET ?"), eq(10), eq(10)))
            .thenReturn(List.of(Map.of("id", "t-1")));

        List<Map<String, Object>> res = service.listTasks(2, 10);

        assertEquals(1, res.size());
        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("LIMIT ? OFFSET ?"), eq(10), eq(10));
    }

    // ── 工具 ──

    private ComplianceRule rule(String id, String name, String domain) {
        ComplianceRule r = new ComplianceRule();
        r.setId(id);
        r.setName(name);
        r.setDomain(domain);
        r.setRuleType("ERROR");
        r.setDescription("d");
        r.setEnabled(true);
        return r;
    }
}
