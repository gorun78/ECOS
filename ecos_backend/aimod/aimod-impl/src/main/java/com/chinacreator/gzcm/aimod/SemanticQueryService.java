package com.chinacreator.gzcm.aimod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语义词典 → ObjectQL 查询条件翻译器。
 *
 * <p>将自然语言业务短语（如"高价值客户""华东优质供应商"）翻译为结构化 ObjectQL 查询条件。</p>
 *
 * <h3>词典条目（硬编码种子）</h3>
 * <ul>
 *   <li>"高价值客户" → Customer.score >= 80</li>
 *   <li>"华东" → Customer.region = "EastChina"</li>
 *   <li>"优质供应商" → Supplier.creditScore >= 90</li>
 *   <li>"近期注册" → Supplier.createdAt > "2025-01-01"</li>
 * </ul>
 *
 * <h3>组合支持</h3>
 * "华东高价值客户" → 合并 region=EastChina + score>=80 两个 filter（AND 逻辑）。
 */
public class SemanticQueryService {

    /** 语义词典：短语 → ObjectQL 片段 */
    private static final Map<String, ObjectQLFragment> DICT = new LinkedHashMap<>();

    static {
        // 条目 1: 高价值客户
        DICT.put("高价值客户", new ObjectQLFragment(
                "Customer",
                "score", ">=", 80));

        // 条目 2: 华东
        DICT.put("华东", new ObjectQLFragment(
                "Customer",
                "region", "=", "EastChina"));

        // 条目 3: 优质供应商
        DICT.put("优质供应商", new ObjectQLFragment(
                "Supplier",
                "creditScore", ">=", 90));

        // 条目 4: 近期注册
        DICT.put("近期注册", new ObjectQLFragment(
                "Supplier",
                "createdAt", ">", "2025-01-01"));
    }

    /**
     * 将中文业务短语翻译为 ObjectQL 查询条件。
     *
     * @param phrase 业务短语（如"高价值客户""华东高价值客户"）
     * @return ObjectQL JSON Map，无匹配返回 null
     */
    public Map<String, Object> translate(String phrase) {
        if (phrase == null || phrase.trim().isEmpty()) {
            return null;
        }

        // 1. 精确匹配
        ObjectQLFragment exact = DICT.get(phrase);
        if (exact != null) {
            return buildObjectQL(exact);
        }

        // 2. 组合匹配 — 尝试用词典条目拆分 phrase
        List<ObjectQLFragment> fragments = matchFragments(phrase);
        if (!fragments.isEmpty()) {
            return mergeObjectQL(fragments);
        }

        // 3. 无匹配
        return null;
    }

    // ═══════════════════════════════════════════════════════
    // 内部方法
    // ═══════════════════════════════════════════════════════

    /**
     * 在 phrase 中查找所有匹配的词典片段。
     * 朴素实现：按 key 长度降序扫描，贪婪匹配（避免"华东"截断"华东高价值"）。
     */
    private List<ObjectQLFragment> matchFragments(String phrase) {
        // 按 key 长度降序（长短语优先匹配）
        List<String> keys = new ArrayList<>(DICT.keySet());
        keys.sort((a, b) -> Integer.compare(b.length(), a.length()));

        List<ObjectQLFragment> result = new ArrayList<>();
        String remaining = phrase;

        for (String key : keys) {
            if (remaining.contains(key)) {
                result.add(DICT.get(key));
                remaining = remaining.replace(key, "").trim();
            }
        }

        return result;
    }

    /**
     * 单个片段 → ObjectQL Map
     */
    private Map<String, Object> buildObjectQL(ObjectQLFragment frag) {
        Map<String, Object> ql = new LinkedHashMap<>();
        ql.put("entity", frag.entity);

        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("field", frag.field);
        filter.put("op", frag.op);
        filter.put("value", frag.value);
        ql.put("filter", filter);

        return ql;
    }

    /**
     * 合并多个片段（同 entity AND 逻辑；跨 entity 用 AND composite）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeObjectQL(List<ObjectQLFragment> fragments) {
        if (fragments.size() == 1) {
            return buildObjectQL(fragments.get(0));
        }

        // 按 entity 分组
        Map<String, List<Map<String, Object>>> byEntity = new LinkedHashMap<>();
        for (ObjectQLFragment frag : fragments) {
            byEntity.computeIfAbsent(frag.entity, k -> new ArrayList<>())
                    .add(buildFilterMap(frag));
        }

        Map<String, Object> ql = new LinkedHashMap<>();

        if (byEntity.size() == 1) {
            // 同 entity → 单 filter 带 AND conditions
            String entity = byEntity.keySet().iterator().next();
            ql.put("entity", entity);
            ql.put("filter", buildCompositeFilter(byEntity.get(entity), "AND"));
        } else {
            // 跨 entity → 顶级 AND composite
            ql.put("composite", "AND");
            List<Map<String, Object>> subQueries = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : byEntity.entrySet()) {
                Map<String, Object> sub = new LinkedHashMap<>();
                sub.put("entity", entry.getKey());
                sub.put("filter", buildCompositeFilter(entry.getValue(), "AND"));
                subQueries.add(sub);
            }
            ql.put("queries", subQueries);
        }

        return ql;
    }

    private Map<String, Object> buildFilterMap(ObjectQLFragment frag) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("field", frag.field);
        f.put("op", frag.op);
        f.put("value", frag.value);
        return f;
    }

    private Map<String, Object> buildCompositeFilter(List<Map<String, Object>> conditions,
                                                      String operator) {
        Map<String, Object> filter = new LinkedHashMap<>();
        if (conditions.size() == 1) {
            return conditions.get(0);
        }
        filter.put("operator", operator);
        filter.put("conditions", conditions);
        return filter;
    }

    // ═══════════════════════════════════════════════════════
    // 内部数据类
    // ═══════════════════════════════════════════════════════

    private static class ObjectQLFragment {
        final String entity;
        final String field;
        final String op;
        final Object value;

        ObjectQLFragment(String entity, String field, String op, Object value) {
            this.entity = entity;
            this.field = field;
            this.op = op;
            this.value = value;
        }
    }
}
