package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 认知计划校验器（详细设计-认知引擎 v1.0 §6.2 第四阶段；CE-05）。
 *
 * <p>覆盖：DAG 无环、依赖可解析、输入/输出 Schema 可解析、能力引用有效、预算不超限、
 * 无未经授权的副作用、权限硬约束。校验不依赖 LLM 自检。</p>
 */
@Component("cognitivePlanValidator")
public class PlanValidator {

    public static final String ISSUE_MISSING_SCHEMA = "MISSING_SCHEMA_REF";
    public static final String ISSUE_MISSING_CAPABILITY = "MISSING_CAPABILITY";
    public static final String ISSUE_UNKNOWN_DEPENDENCY = "UNKNOWN_DEPENDENCY";
    public static final String ISSUE_CYCLE = "DAG_CYCLE";
    public static final String ISSUE_BUDGET = "BUDGET_EXCEEDED";
    public static final String ISSUE_SIDE_EFFECT = "UNAUTHORIZED_SIDE_EFFECT";
    public static final String ISSUE_PERMISSION = "PERMISSION_DENIED";
    public static final String ISSUE_EMPTY_PLAN = "EMPTY_PLAN";

    /**
     * @param plan 计划草案：{@code steps[]}，每步可为 Map（stepId/dependsOn/inputSchemaRef/outputSchemaRef/
     *             requiredCapabilities/estimatedTokens/allowSideEffects）；{@code budget}{maxSteps,maxTokens}；
     *             {@code availableCapabilities[]}；{@code authorized}；{@code sideEffectAuthorized}。
     * @return {@code {passed, dagAcyclic, issues[]}}
     */
    public Map<String, Object> validate(Map<String, Object> plan) {
        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, Object>> steps = steps(plan);

        if (steps.isEmpty()) {
            issue(issues, ISSUE_EMPTY_PLAN, "blocking", "计划没有任何步骤");
            return result(false, true, issues);
        }
        if (Boolean.FALSE.equals(plan.get("authorized"))) {
            issue(issues, ISSUE_PERMISSION, "blocking", "调用者未获授权");
        }

        Map<String, Object> budget = map(plan.get("budget"));
        Set<String> availableCapabilities = stringSet(plan.get("availableCapabilities"));
        Set<String> stepIds = new HashSet<>();
        for (Map<String, Object> step : steps) {
            String id = str(step.get("stepId"));
            if (id != null) {
                stepIds.add(id);
            }
        }

        long tokens = 0;
        for (Map<String, Object> step : steps) {
            String id = str(step.get("stepId"));
            if (isBlank(str(step.get("inputSchemaRef"))) || isBlank(str(step.get("outputSchemaRef")))) {
                issue(issues, ISSUE_MISSING_SCHEMA, "blocking", "步骤 " + id + " 缺少输入/输出 Schema");
            }
            for (String dep : stringSet(step.get("dependsOn"))) {
                if (!stepIds.contains(dep)) {
                    issue(issues, ISSUE_UNKNOWN_DEPENDENCY, "blocking", "步骤 " + id + " 依赖不存在: " + dep);
                }
            }
            for (String cap : stringSet(step.get("requiredCapabilities"))) {
                if (!availableCapabilities.isEmpty() && !availableCapabilities.contains(cap)) {
                    issue(issues, ISSUE_MISSING_CAPABILITY, "blocking", "步骤 " + id + " 能力不可用: " + cap);
                }
            }
            if (Boolean.TRUE.equals(step.get("allowSideEffects"))
                    && !Boolean.TRUE.equals(plan.get("sideEffectAuthorized"))) {
                issue(issues, ISSUE_SIDE_EFFECT, "blocking", "步骤 " + id + " 声明副作用但未获授权");
            }
            Object est = step.get("estimatedTokens");
            tokens += est instanceof Number n ? n.longValue() : 0L;
        }

        int maxSteps = intOf(budget.get("maxSteps"), Integer.MAX_VALUE);
        long maxTokens = longOf(budget.get("maxTokens"), Long.MAX_VALUE);
        if (steps.size() > maxSteps) {
            issue(issues, ISSUE_BUDGET, "blocking", "步骤数 " + steps.size() + " 超预算 " + maxSteps);
        }
        if (tokens > maxTokens) {
            issue(issues, ISSUE_BUDGET, "blocking", "预估 token " + tokens + " 超预算 " + maxTokens);
        }

        boolean acyclic = acyclic(steps, issues);
        boolean passed = issues.stream().noneMatch(i -> "blocking".equals(i.get("severity")));
        return result(passed, acyclic, issues);
    }

    // ── 环检测（DFS）─────────────────────────────────────

    private boolean acyclic(List<Map<String, Object>> steps, List<Map<String, Object>> issues) {
        Map<String, List<String>> adj = new LinkedHashMap<>();
        for (Map<String, Object> step : steps) {
            String id = str(step.get("stepId"));
            if (id != null) {
                adj.put(id, new ArrayList<>(stringSet(step.get("dependsOn"))));
            }
        }
        Map<String, Integer> color = new HashMap<>();
        for (String node : adj.keySet()) {
            if (dfs(node, adj, color)) {
                issue(issues, ISSUE_CYCLE, "blocking", "计划 DAG 存在环路，涉及节点: " + node);
                return false;
            }
        }
        return true;
    }

    private boolean dfs(String node, Map<String, List<String>> adj, Map<String, Integer> color) {
        Integer state = color.get(node);
        if (state != null && state == 1) {
            return true;
        }
        if (state != null && state == 2) {
            return false;
        }
        color.put(node, 1);
        for (String next : adj.getOrDefault(node, List.of())) {
            if (adj.containsKey(next) && dfs(next, adj, color)) {
                return true;
            }
        }
        color.put(node, 2);
        return false;
    }

    // ── 辅助 ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Map<String, Object> plan) {
        Object raw = plan == null ? null : plan.get("steps");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                out.add((Map<String, Object>) item);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object raw) {
        return raw instanceof Map ? (Map<String, Object>) raw : Map.of();
    }

    private static Set<String> stringSet(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private static void issue(List<Map<String, Object>> issues, String code, String severity, String detail) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("code", code);
        issue.put("severity", severity);
        issue.put("detail", detail);
        issues.add(issue);
    }

    private static Map<String, Object> result(boolean passed, boolean acyclic, List<Map<String, Object>> issues) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("passed", passed);
        out.put("dagAcyclic", acyclic);
        out.put("issues", issues);
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static int intOf(Object o, int fallback) {
        return o instanceof Number n ? n.intValue() : fallback;
    }

    private static long longOf(Object o, long fallback) {
        return o instanceof Number n ? n.longValue() : fallback;
    }
}
