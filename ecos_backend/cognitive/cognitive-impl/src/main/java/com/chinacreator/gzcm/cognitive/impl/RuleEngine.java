package com.chinacreator.gzcm.cognitive.impl;

import com.chinacreator.gzcm.cognitive.model.Action;
import com.chinacreator.gzcm.cognitive.model.MatchedRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 轻量 Rete 风格规则推理器。
 *
 * <p>从内存 Map 加载规则，对输入的事实集进行条件匹配，返回命中规则列表。
 * 支持 equals / greaterThan / lessThan / contains / range 五种条件运算符，
 * 命中结果按优先级降序 + 加载时间升序排序。
 *
 * <p>规则内部结构：
 * <pre>{@code
 * {
 *   "ruleName": "high_cpu_alert",
 *   "ruleType": "THRESHOLD",
 *   "priority": 10,
 *   "description": "CPU 使用率过高告警",
 *   "condition": { "fact": "cpuUsage", "operator": "greaterThan", "value": 90 },
 *   "action": { "type": "NOTIFY", "config": { "channel": "wechat", "level": "P1" } }
 * }
 * }</pre>
 *
 * <p>后续对接 {@code ecos_cognitive_rule} 表实现持久化加载。
 *
 * @author DataBridge Team
 */
@Service("ruleEngine")
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    // ── 操作符常量 ──────────────────────────────────────────
    private static final String OP_EQUALS      = "equals";
    private static final String OP_GREATER_THAN = "greaterThan";
    private static final String OP_LESS_THAN    = "lessThan";
    private static final String OP_CONTAINS     = "contains";
    private static final String OP_RANGE        = "range";

    private static final Set<String> KNOWN_OPERATORS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    OP_EQUALS, OP_GREATER_THAN, OP_LESS_THAN, OP_CONTAINS, OP_RANGE)));

    // ── 内部规则结构 ────────────────────────────────────────

    /**
     * 内存中的规则表示，对应 {@code ecos_cognitive_rule} 表行。
     */
    public static class RuleDef {
        final String ruleName;
        final String ruleType;
        final Map<String, Object> condition;  // {fact, operator, value}
        final Map<String, Object> action;     // {type, config}
        final int priority;
        final String description;
        final Instant loadedAt;               // 加载时间，用于排序 tie-break

        RuleDef(String ruleName, String ruleType, Map<String, Object> condition,
                Map<String, Object> action, int priority, String description) {
            this.ruleName = ruleName;
            this.ruleType = ruleType;
            this.condition = condition != null ? condition : Collections.emptyMap();
            this.action    = action    != null ? action    : Collections.emptyMap();
            this.priority  = priority;
            this.description = description;
            this.loadedAt  = Instant.now();
        }

        @Override
        public String toString() {
            return "RuleDef{name=" + ruleName + ", type=" + ruleType +
                    ", priority=" + priority + "}";
        }
    }

    // ── 规则仓库 ────────────────────────────────────────────

    /** 线程安全的内存规则仓库，key = ruleName */
    private final Map<String, RuleDef> ruleStore = new ConcurrentHashMap<>();

    // ── 公开 API ────────────────────────────────────────────

    /**
     * 从 Map 列表批量加载规则，支持后续对接 DB 查询结果。
     *
     * @param ruleMaps 规则定义 Map 列表，每个 Map 的键参见 {@link #loadRule(Map)}
     */
    public void loadRules(List<Map<String, Object>> ruleMaps) {
        if (ruleMaps == null || ruleMaps.isEmpty()) {
            log.warn("loadRules called with empty or null list, skipping");
            return;
        }
        for (Map<String, Object> map : ruleMaps) {
            loadRule(map);
        }
        log.info("Loaded {} rules into rule store", ruleMaps.size());
    }

    /**
     * 从单个 Map 加载一条规则。
     *
     * <p>期望 Map 包含以下键：
     * <ul>
     *   <li>{@code ruleName}   (String, 必填)</li>
     *   <li>{@code ruleType}   (String, 默认 "THRESHOLD")</li>
     *   <li>{@code condition}  (Map&lt;String,Object&gt; — {fact, operator, value})</li>
     *   <li>{@code action}     (Map&lt;String,Object&gt; — {type, config})</li>
     *   <li>{@code priority}   (Integer, 默认 0)</li>
     *   <li>{@code description}(String, 默认 null)</li>
     * </ul>
     *
     * @param ruleMap 单条规则定义
     * @throws IllegalArgumentException 如果 ruleName 缺失
     */
    @SuppressWarnings("unchecked")
    public void loadRule(Map<String, Object> ruleMap) {
        String ruleName = (String) ruleMap.get("ruleName");
        if (ruleName == null || ruleName.trim().isEmpty()) {
            throw new IllegalArgumentException("ruleName is required");
        }

        String ruleType = (String) ruleMap.getOrDefault("ruleType", "THRESHOLD");
        Map<String, Object> condition = (Map<String, Object>) ruleMap.get("condition");
        Map<String, Object> action    = (Map<String, Object>) ruleMap.get("action");
        int priority = toInt(ruleMap.get("priority"), 0);
        String description = (String) ruleMap.get("description");

        RuleDef rule = new RuleDef(ruleName, ruleType, condition, action, priority, description);
        ruleStore.put(ruleName, rule);
        log.debug("Loaded rule: {}", rule);
    }

    /**
     * 移除指定名称的规则。
     *
     * @param ruleName 规则名称
     * @return 被移除的规则，若不存在返回 null
     */
    public RuleDef removeRule(String ruleName) {
        return ruleStore.remove(ruleName);
    }

    /**
     * 获取当前已加载的规则数量。
     */
    public int ruleCount() {
        return ruleStore.size();
    }

    /**
     * 清空所有规则（主要用于测试）。
     */
    public void clearRules() {
        ruleStore.clear();
        log.info("Rule store cleared");
    }

    /**
     * 核心推理方法：对输入事实集进行规则匹配。
     *
     * <p>遍历所有已加载规则，逐一评估条件，命中则生成 {@link MatchedRule}。
     * 结果按优先级降序、加载时间升序排序。
     *
     * @param facts 事实键值对，key 对应条件中的 {@code fact} 字段
     * @return 命中的规则列表（按优先级排序），无命中返回空列表
     */
    public List<MatchedRule> evaluate(Map<String, Object> facts) {
        if (facts == null || facts.isEmpty()) {
            log.debug("evaluate called with empty facts, no rules matched");
            return Collections.emptyList();
        }

        long startNs = System.nanoTime();
        List<MatchedRule> matched = new ArrayList<>();

        for (RuleDef rule : ruleStore.values()) {
            if (matchCondition(rule.condition, facts)) {
                MatchedRule mr = new MatchedRule();
                mr.setRuleId(rule.ruleName);
                mr.setRuleName(rule.ruleName);
                mr.setDescription(rule.description);
                mr.setConfidence(calculateConfidence(rule.condition, facts));
                mr.setActions(buildActions(rule.action));
                matched.add(mr);

                log.debug("Rule matched: {} (confidence={})", rule.ruleName, mr.getConfidence());
            }
        }

        // 排序：优先级降序 → 加载时间升序（先加载的规则优先，体现 thenByTime）
        matched.sort(Comparator
                .<MatchedRule>comparingInt(mr -> {
                    RuleDef r = ruleStore.get(mr.getRuleName());
                    return r != null ? r.priority : 0;
                })
                .reversed()
                .thenComparing(mr -> {
                    RuleDef r = ruleStore.get(mr.getRuleName());
                    return r != null ? r.loadedAt : Instant.MAX;
                }));

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("evaluate completed: {} facts → {} matched rules in {} ms",
                facts.size(), matched.size(), elapsedMs);

        return matched;
    }

    // ── 条件匹配核心 ─────────────────────────────────────────

    /**
     * 评估单条规则的 condition 是否被 facts 满足。
     */
    @SuppressWarnings("unchecked")
    private boolean matchCondition(Map<String, Object> condition, Map<String, Object> facts) {
        if (condition == null || condition.isEmpty()) {
            return false;
        }

        String factName = (String) condition.get("fact");
        String operator = (String) condition.get("operator");
        Object expected  = condition.get("value");

        if (factName == null || operator == null) {
            log.warn("Invalid condition — missing 'fact' or 'operator': {}", condition);
            return false;
        }

        Object actual = resolveFact(factName, facts);
        if (actual == null) {
            return false;  // 事实不存在，不匹配
        }

        // 根据操作符分发
        switch (operator) {
            case OP_EQUALS:
                return valueEquals(actual, expected);

            case OP_GREATER_THAN:
                return compareValues(actual, expected) > 0;

            case OP_LESS_THAN:
                return compareValues(actual, expected) < 0;

            case OP_CONTAINS:
                return String.valueOf(actual)
                        .contains(String.valueOf(expected));

            case OP_RANGE:
                if (expected instanceof List) {
                    List<Object> range = (List<Object>) expected;
                    if (range.size() < 2) {
                        log.warn("Range condition requires [min, max], got: {}", range);
                        return false;
                    }
                    Object min = range.get(0);
                    Object max = range.get(1);
                    return compareValues(actual, min) >= 0
                            && compareValues(actual, max) <= 0;
                }
                if (expected instanceof Map) {
                    Map<String, Object> rangeMap = (Map<String, Object>) expected;
                    Object rmin = rangeMap.get("min");
                    Object rmax = rangeMap.get("max");
                    boolean inRange = true;
                    if (rmin != null) inRange &= compareValues(actual, rmin) >= 0;
                    if (rmax != null) inRange &= compareValues(actual, rmax) <= 0;
                    return inRange;
                }
                log.warn("Range condition — unsupported value format: {}", expected);
                return false;

            default:
                log.warn("Unknown operator '{}'. Known: {}", operator, KNOWN_OPERATORS);
                return false;
        }
    }

    // ── 事实解析（支持点号路径） ─────────────────────────────

    /**
     * 从 facts Map 解析嵌套路径，如 {@code "metrics.cpu.usage"}。
     */
    @SuppressWarnings("unchecked")
    private Object resolveFact(String factName, Map<String, Object> facts) {
        if (factName.contains(".")) {
            String[] parts = factName.split("\\.");
            Object current = facts;
            for (String part : parts) {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(part);
                } else {
                    return null;
                }
                if (current == null) return null;
            }
            return current;
        }
        return facts.get(factName);
    }

    // ── 值比较工具 ──────────────────────────────────────────

    /**
     * 相等性比较，数值类型统一转为 {@link BigDecimal} 比较。
     */
    private boolean valueEquals(Object actual, Object expected) {
        if (actual == null || expected == null) return false;
        if (actual.equals(expected)) return true;
        if (isNumeric(actual) && isNumeric(expected)) {
            return toBigDecimal(actual).compareTo(toBigDecimal(expected)) == 0;
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    /**
     * 数值大小比较。返回 &gt;0 表示 actual &gt; expected。
     */
    private int compareValues(Object actual, Object expected) {
        if (isNumeric(actual) && isNumeric(expected)) {
            return toBigDecimal(actual).compareTo(toBigDecimal(expected));
        }
        // 非数值降级为字符串字典序比较
        return String.valueOf(actual).compareTo(String.valueOf(expected));
    }

    // ── 置信度计算 ──────────────────────────────────────────

    /**
     * 根据匹配程度估算置信度 (0.0 ~ 1.0)。
     *
     * <ul>
     *   <li>equals / contains → 完全匹配 = 1.0</li>
     *   <li>greaterThan / lessThan → 1.0</li>
     *   <li>range → 基于实际值在区间内位置加权 (0.8 ~ 1.0)</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private double calculateConfidence(Map<String, Object> condition, Map<String, Object> facts) {
        if (condition == null) return 0.0;

        String factName = (String) condition.get("fact");
        String operator = (String) condition.get("operator");
        Object expected  = condition.get("value");
        Object actual    = resolveFact(factName, facts);

        if (actual == null || operator == null) return 0.0;

        switch (operator) {
            case OP_EQUALS:
            case OP_CONTAINS:
                return 1.0;

            case OP_GREATER_THAN:
            case OP_LESS_THAN:
                return 1.0;

            case OP_RANGE:
                if (expected instanceof List && isNumeric(actual)) {
                    List<Object> range = (List<Object>) expected;
                    if (range.size() < 2) return 0.8;
                    BigDecimal act = toBigDecimal(actual);
                    BigDecimal min = toBigDecimal(range.get(0));
                    BigDecimal max = toBigDecimal(range.get(1));
                    if (max.compareTo(min) <= 0) return 0.8;
                    // 越靠近区间中心置信度越高
                    BigDecimal mid = min.add(max).divide(BigDecimal.valueOf(2));
                    BigDecimal half = max.subtract(min).divide(BigDecimal.valueOf(2));
                    BigDecimal distFromMid = act.subtract(mid).abs();
                    // distance ratio: 0 → 1.0, half → 0.8
                    double ratio = distFromMid.divide(half, 10, java.math.RoundingMode.HALF_UP)
                            .doubleValue();
                    return Math.max(0.8, 1.0 - 0.2 * ratio);
                }
                if (expected instanceof Map) {
                    return 0.9; // Map 形式的 range 默认置信度
                }
                return 0.8;

            default:
                return 0.5;
        }
    }

    // ── 动作构建 ────────────────────────────────────────────

    /**
     * 将规则内部的 action Map 转换为 {@link Action} 列表。
     *
     * <p>action 结构：{@code {type: "NOTIFY", config: {channel: "wechat"}}}
     */
    @SuppressWarnings("unchecked")
    private List<Action> buildActions(Map<String, Object> actionDef) {
        if (actionDef == null || actionDef.isEmpty()) {
            return Collections.emptyList();
        }

        Action action = new Action();
        action.setType((String) actionDef.get("type"));

        Object configObj = actionDef.get("config");
        if (configObj instanceof Map) {
            action.setParams((Map<String, Object>) configObj);
        }
        // 兼容：如果 actionDef 没有 "config" 包装，直接把剩余键视为参数
        else if (configObj == null && actionDef.size() > 1) {
            Map<String, Object> params = new LinkedHashMap<>(actionDef);
            params.remove("type");
            if (!params.isEmpty()) {
                action.setParams(params);
            }
        }

        return Collections.singletonList(action);
    }

    // ── 数值工具 ────────────────────────────────────────────

    private static boolean isNumeric(Object obj) {
        return obj instanceof Number;
    }

    private static BigDecimal toBigDecimal(Object obj) {
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) {
            return BigDecimal.valueOf(((Number) obj).doubleValue());
        }
        return new BigDecimal(String.valueOf(obj));
    }

    private static int toInt(Object obj, int defaultValue) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        if (obj instanceof String) {
            try { return Integer.parseInt((String) obj); }
            catch (NumberFormatException ignored) { /* fall through */ }
        }
        return defaultValue;
    }
}
