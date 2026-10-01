package com.chinacreator.gzcm.engine.ontology.contract;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F03-05 / PRD-03 §3.3 — 实体映射契约三用例（table-driven，JUnit5）。
 *
 * <p>语义由 {@link EntityMappingGuard} 纯函数沉淀，本测试锁定：</p>
 * <ol>
 *   <li><b>换列生效</b>：映射 {@code Project/physicalColumn=project_code}，映射更新后
 *       解析结果随物理列改变（mock mapper pre/post update 的差异断言）；</li>
 *   <li><b>指向不存在列产生 INVALID_MAPPING</b>：physicalColumn 不在事实表已知列集 → 产生
 *       {@code INVALID_MAPPING} issue（不实例化）；</li>
 *   <li><b>schema diff 只增</b>：s2 比 s1 多一个字段 → 只增通过（无 missing/renamed）。</li>
 * </ol>
 */
class EntityMappingContractCasesTest {

    private static Map<String, Object> fm(String entityField, String physicalColumn) {
        return Map.of("entityField", entityField, "physicalColumn", physicalColumn);
    }

    // ── 用例 1：换列生效 ────────────────────────────────────────────

    @Test
    void renameOrCorrectColumnIsHonored() {
        // 换列前：project 实体指向 project_id
        List<Map<String, Object>> before = List.of(fm("project", "project_id"));
        // 换列后：映射更新为 project_code（物理列被更正）
        List<Map<String, Object>> after = List.of(fm("project", "project_code"));

        assertEquals("project_id", EntityMappingGuard.resolvePhysicalColumn("project", before),
                "换列前解析到旧物理列");
        assertEquals("project_code", EntityMappingGuard.resolvePhysicalColumn("project", after),
                "换列生效：解析到更正后的新物理列 project_code");
        // 换列本身（同一 entityField 的 physicalColumn 变更）不触发 diff 只增告警——那是 rename
        // 场景，由业务侧另裁；此处仅锁定"解析反映最新映射"。
        assertFalse(EntityMappingGuard.resolvePhysicalColumn("project", after).equals("project_id"),
                "解析必须反映最新的 physicalColumn");
    }

    // ── 用例 2：指向不存在列 → INVALID_MAPPING ─────────────────────

    @Test
    void pointerToNonExistentColumnProducesInvalidMapping() {
        // 事实表已知列（mock mapper 的列清单）
        Set<String> knownColumns = Set.of("project_id", "customer_id", "amount");
        // 映射里 physicalColumn 指向 ghost_col（不在已知列集）
        List<Map<String, Object>> mappings = List.of(fm("project", "project_id"), fm("snapshot", "ghost_col"));

        List<String> issues = EntityMappingGuard.validateColumns(mappings, knownColumns);
        assertEquals(1, issues.size(), "仅一条映射违例，应产生一个 issue");
        assertTrue(issues.get(0).startsWith(EntityMappingGuard.INVALID_MAPPING),
                "issue 必须以 INVALID_MAPPING 前缀开始，实际：" + issues.get(0));

        // 全部列合法 → 无 issue
        List<String> ok = EntityMappingGuard.validateColumns(
                List.of(fm("project", "project_id"), fm("amount", "amount")), knownColumns);
        assertTrue(ok.isEmpty(), "无违例时应无 issue，实际：" + ok);
    }

    // ── 用例 3：schema diff 只增 ────────────────────────────────────

    @Test
    void schemaDiffOnlyAddedFieldsPass() {
        // s1（旧快照）：2 字段
        List<Map<String, Object>> s1 = List.of(
                fm("project_id", "project_id"),
                fm("customer_id", "customer_id"));
        // s2（新快照）：s1 + 一个新增字段 contract_id
        List<Map<String, Object>> s2 = List.of(
                fm("project_id", "project_id"),
                fm("customer_id", "customer_id"),
                fm("contract_id", "contract_id"));

        List<String> problems = EntityMappingGuard.diffOnlyAddAllowed(s1, s2);
        assertTrue(problems.isEmpty(), "schema diff 只增应通过，实际：" + problems);
    }

    @Test
    void schemaDiffRemovedFieldFails() {
        // 反例：s2 丢失 customer_id → MISSING 违例
        List<Map<String, Object>> s1 = List.of(
                fm("project_id", "project_id"),
                fm("customer_id", "customer_id"));
        List<Map<String, Object>> s2 = List.of(
                fm("project_id", "project_id"));

        List<String> problems = EntityMappingGuard.diffOnlyAddAllowed(s1, s2);
        assertFalse(problems.isEmpty(), "缺失字段必须产生违例");
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("MISSING")), "应报 MISSING");
    }
}
