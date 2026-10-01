package com.chinacreator.gzcm.engine.ontology.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F03-05 — 实体映射契约的<b>测试本地语义守卫</b>（contract helper）。
 *
 * <p>把 PRD-03 §3.3 三用例的合同语义（换列生效 / 无效列产生 INVALID_MAPPING /
 * schema diff 只增）沉淀为<b>纯函数</b>，供 {@link EntityMappingContractCasesTest}
 * 表驱动断言，未来 kb {@code KbEntityInstanceExtractionService}（或后续 mapper）可直接复用。
 * 本类是纯函数、无副作用，锁定语义由用例测试固化。</p>
 */
public final class EntityMappingGuard {

    /** F03-05 rule：无效映射（物理列指向不存在的列）。 */
    public static final String INVALID_MAPPING = "INVALID_MAPPING";

    private EntityMappingGuard() {
    }

    /**
     * 用例 1（换列生效）：按 entityField 解析映射表当前生效的物理列。
     *
     * @param entityField    逻辑字段
     * @param fieldMappings  {@code {entityField, physicalColumn, transform}} 行集
     * @return 映射到的物理列；未找到 → null
     */
    public static String resolvePhysicalColumn(String entityField, List<Map<String, Object>> fieldMappings) {
        if (fieldMappings == null) return null;
        for (Map<String, Object> fm : fieldMappings) {
            if (entityField == null ? fm.get("entityField") == null : entityField.equals(asText(fm.get("entityField")))) {
                return asText(fm.get("physicalColumn"));
            }
        }
        return null;
    }

    /**
     * 用例 2（指向不存在列产生 INVALID_MAPPING）：校验每张 fieldMappings 的物理列都在事实表已知列集内。
     *
     * @param fieldMappings 映射行集
     * @param knownColumns  事实表已知列集（mock mapper 的列清单）
     * @return 违例描述列表；有效映射返回空
     */
    public static List<String> validateColumns(List<Map<String, Object>> fieldMappings, Set<String> knownColumns) {
        List<String> issues = new ArrayList<>();
        if (fieldMappings == null) return issues;
        Set<String> known = (knownColumns == null) ? Set.of() : knownColumns;
        for (Map<String, Object> fm : fieldMappings) {
            String entityField = asText(fm.get("entityField"));
            String physicalColumn = asText(fm.get("physicalColumn"));
            if (physicalColumn == null || !known.contains(physicalColumn)) {
                issues.add(INVALID_MAPPING + ": entityField '" + entityField
                        + "' -> physicalColumn '" + physicalColumn + "' not in fact table");
            }
        }
        return issues;
    }

    /**
     * 用例 3（schema diff 只增）：before → after 只允许"新增"字段；
     * before 已有字段不得缺失或改名（必要时物理列变更）。
     *
     * <p>入参形态：每行 {@code Map} 代表 schema 一个字段，主索引 {@code entityField}，
     * 附带 {@code physicalColumn}（可选）。s2 相比 s1 多一个字段（严格超集）→ 空返回（通过）；
     * 缺失或 entityField→physicalColumn 被改动（改名）→ 报违例。</p>
     *
     * @param before 旧 schema 快照
     * @param after  新 schema 快照
     * @return 违例描述列表；只增 → 空（通过）
     */
    public static List<String> diffOnlyAddAllowed(List<Map<String, Object>> before, List<Map<String, Object>> after) {
        List<String> problems = new ArrayList<>();
        Map<String, String> beforeCols = toFieldColumnMap(before);
        Map<String, String> afterCols = toFieldColumnMap(after);

        for (Map.Entry<String, String> e : beforeCols.entrySet()) {
            String field = e.getKey();
            String afterColumn = afterCols.get(field);
            if (afterColumn == null) {
                problems.add("MISSING: field '" + field + "' removed or renamed");
            } else if (!e.getValue().equals(afterColumn)) {
                problems.add("RENAMED: field '" + field + "' physicalColumn changed '"
                        + e.getValue() + "' -> '" + afterColumn + "'");
            }
        }
        return problems;
    }

    private static Map<String, String> toFieldColumnMap(List<Map<String, Object>> rows) {
        Map<String, String> m = new LinkedHashMap<>();
        if (rows == null) return m;
        for (Map<String, Object> r : rows) {
            String f = asText(r.get("entityField"));
            if (f != null) m.put(f, asText(r.get("physicalColumn")));
        }
        return m;
    }

    private static String asText(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
