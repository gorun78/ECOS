package com.chinacreator.gzcm.engine.ai.guardrail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * security-engine 裁决返回的执行义务（obligations）—— 详细设计-06 F06-02-2。
 *
 * <p>铁律 §2.4-1/2/3：脱敏与行过滤的<b>算法只在 security 侧</b>，本类只是载荷容器，
 * ai-engine 内禁止出现任何脱敏/行过滤算法实现（验收：
 * {@code ObligationsApplyTest#engineDoesNotSelfImplementMasking}）。</p>
 *
 * <p>形态为跨册接缝，内部结构归分册 01（SEC-01/02）。本类按 security 现契约读取
 * 两类义务：{@code mask[]}（列名 + 规则名）与 {@code rowFilter}（RLS 表名/主体维度的行过滤声明）。</p>
 */
public final class ToolObligations {

    /** 单条列脱敏义务 */
    public record Mask(String column, String rule) {
    }

    private static final ToolObligations NONE = new ToolObligations(List.of(), null, Map.of());

    private final List<Mask> mask;
    private final String rowFilter;
    private final Map<String, Object> raw;

    public ToolObligations(List<Mask> mask, String rowFilter, Map<String, Object> raw) {
        this.mask = mask == null ? List.of() : List.copyOf(mask);
        this.rowFilter = rowFilter;
        this.raw = raw == null ? Map.of() : Map.copyOf(raw);
    }

    public static ToolObligations none() { return NONE; }

    public List<Mask> mask() { return mask; }
    public String rowFilter() { return rowFilter; }
    public Map<String, Object> raw() { return raw; }

    public boolean isEmpty() { return mask.isEmpty() && (rowFilter == null || rowFilter.isBlank()); }

    /**
     * 从 security {@code policy-engine/evaluate} 响应的 {@code data} 段解析 obligations。
     *
     * <p>解析失败按"无义务"处理是<b>不允许</b>的：调用方（裁决闸）在 security 响应不可解析时
     * 已整体 FAIL_CLOSED，本方法只处理"响应可解析且确实没带义务"的情形。</p>
     *
     * <p>两种载体形态都支持（分册 01 现契约 {@code OpaPolicyService.evaluate} 回
     * <b>数组</b>，PRD 表述为 <b>对象</b>）：{@code {mask:[…], rowFilter:"…"}} 与
     * {@code [{column,rule},…]}；数组内的 {@code mask}/{@code maskHints} 子数组会展开。</p>
     */
    public static ToolObligations fromEvaluateData(Map<String, Object> data) {
        if (data == null) return NONE;
        Object ob = data.get("obligations");
        List<Mask> masks = new ArrayList<>();
        String[] rowFilterHolder = new String[1];
        Map<String, Object> rawMap;
        if (ob instanceof Map<?, ?> m) {
            rawMap = castMap(m);
            collectFromMap(rawMap, masks, rowFilterHolder);
        } else if (ob instanceof List<?> list) {
            Map<String, Object> aggregated = new LinkedHashMap<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> item) {
                    Map<String, Object> im = castMap(item);
                    collectFromMap(im, masks, rowFilterHolder);
                    aggregated.putAll(im);
                } else if (o instanceof String s && !s.isBlank()) {
                    masks.add(new Mask(s.trim(), null));
                }
            }
            rawMap = aggregated;
        } else if (ob == null) {
            rawMap = Map.of();
        } else {
            return NONE;
        }
        String rowFilter = rowFilterHolder[0];
        if (masks.isEmpty() && (rowFilter == null || rowFilter.isBlank())) return NONE;
        return new ToolObligations(masks, rowFilter, rawMap);
    }

    /** 从单个义务对象收集 mask 与 rowFilter（可递归一层子数组）。 */
    @SuppressWarnings("unchecked")
    private static void collectFromMap(Map<String, Object> map, List<Mask> masks, String[] rowFilterHolder) {
        Object maskObj = firstNonNull(map.get("mask"), map.get("maskHints"));
        if (maskObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> item) {
                    addMask(castMap(item), masks);
                } else if (o instanceof String s && !s.isBlank()) {
                    masks.add(new Mask(s.trim(), null));
                }
            }
        } else if (map.containsKey("column") || map.containsKey("field")) {
            addMask(map, masks);
        }
        String rf = str(map.get("rowFilter"), str(map.get("row_filter"), null));
        if (rf != null && (rowFilterHolder[0] == null || rowFilterHolder[0].isBlank())) {
            rowFilterHolder[0] = rf;
        }
    }

    private static void addMask(Map<String, Object> im, List<Mask> masks) {
        String column = str(im.get("column"), str(im.get("field"), null));
        String rule = str(im.get("rule"), str(im.get("strategy"), null));
        if (column != null && !column.isBlank() && rule != null && !rule.isBlank()) {
            masks.add(new Mask(column.trim(), rule.trim()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    /**
     * 审计/台账用摘要：只含列名与规则名，不含任何数据值（F06-03 要点 2）。
     */
    public List<Map<String, Object>> summary() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Mask m : mask) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "MASK");
            item.put("column", m.column());
            item.put("rule", m.rule());
            out.add(item);
        }
        if (rowFilter != null && !rowFilter.isBlank()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "ROW_FILTER");
            item.put("declared", true);
            out.add(item);
        }
        return out;
    }

    private static String str(Object v, String fallback) {
        if (v == null) return fallback;
        String s = String.valueOf(v);
        return s.isBlank() ? fallback : s;
    }

    @Override
    public String toString() {
        return "ToolObligations{mask=" + mask.size() + ", rowFilter=" + (rowFilter != null) + "}";
    }
}
