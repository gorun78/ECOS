package com.chinacreator.gzcm.engine.ai.guardrail;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;

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
 *
 * <p><b>#142/B4-3d-4 起还读第三类</b>：{@code rateLimit}（限流义务，差距 D-B4-21 的收口点）。
 * 本类对它的处理到"读成显式三态"为止（未声明／声明但键不可用／可用键），
 * 键名与键前缀一律经 {@link GuardrailDecisionContract#readRateLimitDeclaration} 单源读数；
 * <b>限流的执行不在本类也不在 {@code ToolObligationApplier}</b> —— 它是"这次调用能否发起"的判断，
 * 必须在 dispatch 之前消费（{@code ToolExecutorService} 阶段 2.5），
 * 而 {@code ToolObligationApplier} 处理的是"结果回 LLM 之前"的列/行义务。</p>
 */
public final class ToolObligations {

    /** 单条列脱敏义务 */
    public record Mask(String column, String rule) {
    }

    private static final ToolObligations NONE = new ToolObligations(
            List.of(), null, GuardrailDecisionContract.RateLimitDeclaration.undeclared(), Map.of());

    private final List<Mask> mask;
    private final String rowFilter;
    /** 限流义务的声明形态读数（{@code declared=false} = 策略没签发限流义务）。 */
    private final GuardrailDecisionContract.RateLimitDeclaration rateLimit;
    private final Map<String, Object> raw;

    /** 旧三参形态：无限流义务声明。保持既有调用位编译面（只增不改）。 */
    public ToolObligations(List<Mask> mask, String rowFilter, Map<String, Object> raw) {
        this(mask, rowFilter, GuardrailDecisionContract.RateLimitDeclaration.undeclared(), raw);
    }

    public ToolObligations(List<Mask> mask, String rowFilter,
                           GuardrailDecisionContract.RateLimitDeclaration rateLimit,
                           Map<String, Object> raw) {
        this.mask = mask == null ? List.of() : List.copyOf(mask);
        this.rowFilter = rowFilter;
        this.rateLimit = rateLimit == null
                ? GuardrailDecisionContract.RateLimitDeclaration.undeclared() : rateLimit;
        this.raw = raw == null ? Map.of() : Map.copyOf(raw);
    }

    public static ToolObligations none() { return NONE; }

    public List<Mask> mask() { return mask; }
    public String rowFilter() { return rowFilter; }
    public Map<String, Object> raw() { return raw; }

    /** 策略是否签发了 {@code rateLimit} 义务 —— 与「键是否可用」是两个问题，禁折叠（D-B4-21）。 */
    public boolean declaresRateLimit() { return rateLimit.declared(); }

    /** 限流键原文；未声明或键缺失时为 null，是否声明过一律看 {@link #declaresRateLimit()}。 */
    public String rateLimitKey() { return rateLimit.key(); }

    /** 本批起限流义务也计入「非空」：此前只算 mask/rowFilter ⇒ 仅限流义务的裁决被读成无义务。 */
    public boolean isEmpty() {
        return mask.isEmpty() && (rowFilter == null || rowFilter.isBlank()) && !rateLimit.declared();
    }

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
        Object ob = data.get(GuardrailDecisionContract.KEY_OBLIGATIONS);
        // 限流读数走单源读数点：必须在结构解析分支之前拿到，否则"仅有限流义务"
        // 会落到下面的 return NONE（那正是 D-B4-21 的丢义务形态）
        GuardrailDecisionContract.RateLimitDeclaration rateLimit =
                GuardrailDecisionContract.readRateLimitDeclaration(ob);
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
        if (masks.isEmpty() && (rowFilter == null || rowFilter.isBlank()) && !rateLimit.declared()) {
            return NONE;
        }
        return new ToolObligations(masks, rowFilter, rateLimit, rawMap);
    }

    /** 从单个义务对象收集 mask 与 rowFilter（可递归一层子数组）。 */
    @SuppressWarnings("unchecked")
    private static void collectFromMap(Map<String, Object> map, List<Mask> masks, String[] rowFilterHolder) {
        Object maskObj = firstNonNull(
                map.get(GuardrailDecisionContract.OBLIGATION_MASK),
                map.get(GuardrailDecisionContract.KEY_MASK_HINTS));
        if (maskObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> item) {
                    addMask(castMap(item), masks);
                } else if (o instanceof String s && !s.isBlank()) {
                    masks.add(new Mask(s.trim(), null));
                }
            }
        } else if (map.containsKey(GuardrailDecisionContract.KEY_MASK_COLUMN)
                || map.containsKey(GuardrailDecisionContract.KEY_MASK_COLUMN_ALT)) {
            addMask(map, masks);
        }
        String rf = str(map.get(GuardrailDecisionContract.KEY_ROW_FILTER),
                str(map.get(GuardrailDecisionContract.KEY_ROW_FILTER_ALT), null));
        if (rf != null && (rowFilterHolder[0] == null || rowFilterHolder[0].isBlank())) {
            rowFilterHolder[0] = rf;
        }
    }

    private static void addMask(Map<String, Object> im, List<Mask> masks) {
        String column = str(im.get(GuardrailDecisionContract.KEY_MASK_COLUMN),
                str(im.get(GuardrailDecisionContract.KEY_MASK_COLUMN_ALT), null));
        String rule = str(im.get(GuardrailDecisionContract.KEY_MASK_RULE_ALT),
                str(im.get(GuardrailDecisionContract.KEY_MASK_STRATEGY), null));
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
        if (rateLimit.declared()) {
            // 限流义务入台账：记 declared 与键是否可用，键原文只在可用时记（审计面不记自造键）
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "RATE_LIMIT");
            item.put("declared", true);
            item.put("keyUsable", rateLimit.key() != null && !rateLimit.key().isBlank());
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
        return "ToolObligations{mask=" + mask.size() + ", rowFilter=" + (rowFilter != null)
                + ", rateLimit=" + rateLimit.declared() + "}";
    }
}
