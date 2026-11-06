package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import com.chinacreator.gzcm.engine.cognitive.api.framework.CognitiveFrameworkDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 认知策略选择器（详细设计-认知引擎 v1.0 §6.2；CE-04）。
 *
 * <p>四阶段：硬约束过滤（状态/适用域/输入/依赖/权限/风险/预算/契约）→ 确定性排序（仅排序，
 * 不输出概率或置信度）→ 组装计划 DAG → 计划校验。任一硬约束失败即排除，排除原因码必返；
 * 评分不得覆盖硬约束。真正的执行仍由 W Agent Runtime / ToolExecutor 承担。</p>
 */
@Service
public class FrameworkSelectionService {

    public static final String EXCLUDED_PROBLEM_TYPE = "PROBLEM_TYPE_MISMATCH";
    public static final String EXCLUDED_DOMAIN = "DOMAIN_MISMATCH";
    public static final String EXCLUDED_MISSING_INPUT = "MISSING_INPUT";
    public static final String EXCLUDED_MISSING_CAPABILITY = "MISSING_CAPABILITY";
    public static final String EXCLUDED_CONTRACT = "CONTRACT_UNRESOLVABLE";
    public static final String EXCLUDED_RISK_APPROVAL = "RISK_APPROVAL_PENDING";
    public static final String EXCLUDED_PERMISSION = "PERMISSION_DENIED";
    public static final String EXCLUDED_BUDGET = "BUDGET_EXCEEDED";

    private final CognitiveFrameworkRegistryService registry;
    private final PlanValidator planValidator;

    public FrameworkSelectionService(CognitiveFrameworkRegistryService registry, PlanValidator planValidator) {
        this.registry = registry;
        this.planValidator = planValidator;
    }

    /**
     * @param request problemType/domain/presentInputs/availableCapabilities/authorized/
     *                riskLevel/riskApproval/budget/contextGaps
     */
    public Map<String, Object> select(Map<String, Object> request) {
        Map<String, Object> req = request == null ? Map.of() : request;
        String problemType = str(req.get("problemType"));
        String domain = str(req.get("domain"));
        Set<String> presentInputs = stringSet(req.get("presentInputs"));
        Set<String> availableCapabilities = stringSet(req.get("availableCapabilities"));
        boolean authorized = !Boolean.FALSE.equals(req.get("authorized"));
        String riskLevel = str(req.get("riskLevel"));
        String riskApproval = str(req.get("riskApproval"));
        Map<String, Object> budget = map(req.get("budget"));
        int maxSteps = intOf(budget.get("maxSteps"), Integer.MAX_VALUE);
        int maxFrameworks = intOf(budget.get("maxFrameworks"), Integer.MAX_VALUE);

        List<CognitiveFrameworkDefinition> candidates =
                registry.list(null, null, "PUBLISHED");

        List<Map<String, Object>> selected = new ArrayList<>();
        List<Map<String, Object>> excluded = new ArrayList<>();
        List<String> missingInputs = new ArrayList<>();
        List<Map<String, Object>> ranked = new ArrayList<>();

        for (CognitiveFrameworkDefinition f : candidates) {
            List<String> reasons = new ArrayList<>();
            if (!authorized) {
                reasons.add(EXCLUDED_PERMISSION);
            }
            if (!problemType.isBlank() && f.getProblemTypes() != null && !f.getProblemTypes().isEmpty()
                    && !f.getProblemTypes().contains(problemType)) {
                reasons.add(EXCLUDED_PROBLEM_TYPE);
            }
            if (!domain.isBlank() && f.getDomains() != null && !f.getDomains().isEmpty()
                    && !f.getDomains().contains(domain)) {
                reasons.add(EXCLUDED_DOMAIN);
            }
            for (String pre : nullSafe(f.getPreconditions())) {
                if (!presentInputs.contains(pre)) {
                    reasons.add(EXCLUDED_MISSING_INPUT + ":" + pre);
                    if (!missingInputs.contains(pre)) {
                        missingInputs.add(pre);
                    }
                }
            }
            for (String cap : nullSafe(f.getRequiredCapabilities())) {
                if (!availableCapabilities.isEmpty() && !availableCapabilities.contains(cap)) {
                    reasons.add(EXCLUDED_MISSING_CAPABILITY + ":" + cap);
                }
            }
            if (!f.isContractResolvable()) {
                reasons.add(EXCLUDED_CONTRACT);
            }
            if (riskApprovalRequired(f) && "HIGH".equalsIgnoreCase(riskLevel)
                    && !"APPROVED".equalsIgnoreCase(riskApproval)) {
                reasons.add(EXCLUDED_RISK_APPROVAL);
            }
            int stepCount = f.getSteps() == null ? 0 : f.getSteps().size();
            if (stepCount > maxSteps) {
                reasons.add(EXCLUDED_BUDGET + ":steps=" + stepCount + ">" + maxSteps);
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("frameworkId", f.getFrameworkId());
            row.put("version", f.getVersion());
            row.put("name", f.getName());
            row.put("category", f.getCategory());
            if (reasons.isEmpty()) {
                row.put("score", score(f, problemType, domain, presentInputs, availableCapabilities));
                row.put("scoreKind", "ORDINAL_ONLY");
                row.put("calibrated", false);
                row.put("confidence", null);
                row.put("selectionReason", "HARD_FILTERS_PASSED");
                ranked.add(row);
            } else {
                row.put("reasons", reasons);
                excluded.add(row);
            }
        }

        ranked.sort(Comparator
                .comparingInt((Map<String, Object> m) -> -intOf(m.get("score"), 0))
                .thenComparing(m -> str(m.get("frameworkId")))
                .thenComparing(m -> str(m.get("version"))));

        if (ranked.size() > maxFrameworks) {
            for (Map<String, Object> drop : ranked.subList(maxFrameworks, ranked.size())) {
                Map<String, Object> row = new LinkedHashMap<>(drop);
                row.remove("score");
                row.put("reasons", List.of(EXCLUDED_BUDGET + ":maxFrameworks"));
                excluded.add(row);
            }
            ranked = new ArrayList<>(ranked.subList(0, maxFrameworks));
        }
        selected.addAll(ranked);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("selectedFrameworks", selected);
        out.put("excludedFrameworks", excluded);
        out.put("missingInputs", missingInputs);
        out.put("selectionTrace", trace(problemType, domain, candidates.size(), selected, excluded));
        out.put("usedFrameworkVersions", usedVersions(selected));
        out.put("riskDecision", riskDecision(riskLevel, riskApproval));

        if (selected.isEmpty()) {
            out.put("status", "NEEDS_INPUT_OR_HUMAN");
            out.put("planDraft", null);
            out.put("validationRequirements", List.of());
            out.put("stopConditions", List.of());
            return out;
        }

        Map<String, Object> plan = composePlan(selected, req, budget, availableCapabilities);
        Map<String, Object> validation = planValidator.validate(plan);
        out.put("planDraft", plan);
        out.put("planValidation", validation);
        out.put("validationRequirements", collectStop(selected));
        out.put("stopConditions", plan.get("stopConditions"));
        out.put("status", Boolean.TRUE.equals(validation.get("passed")) ? "PLANNED" : "REPLAN_OR_REJECT");
        if (!Boolean.TRUE.equals(validation.get("passed"))) {
            // 动态改变计划必须有痕：记录重规划原因与实际使用的框架版本（清单阶段4）
            out.put("planRevision", planRevision(validation, selected));
        }
        return out;
    }

    private static List<Map<String, Object>> usedVersions(List<Map<String, Object>> selected) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : selected) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("frameworkId", str(f.get("frameworkId")));
            m.put("version", str(f.get("version")));
            m.put("selectionReason", str(f.get("selectionReason")));
            out.add(m);
        }
        return out;
    }

    private static Map<String, Object> planRevision(Map<String, Object> validation,
                                                    List<Map<String, Object>> selected) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("decision", "REPLAN_OR_REJECT");
        m.put("reason", "PLAN_VALIDATION_FAILED");
        m.put("issues", validation.getOrDefault("issues", List.of()));
        m.put("usedFrameworkVersions", usedVersions(selected));
        return m;
    }

    /** 计划草案：线性 DAG（每步依赖前一步），显式携带版本、契约、能力、预算与停止条件。 */
    private Map<String, Object> composePlan(List<Map<String, Object>> selected, Map<String, Object> req,
                                            Map<String, Object> budget, Set<String> availableCapabilities) {
        List<Map<String, Object>> steps = new ArrayList<>();
        List<String> stopConditions = new ArrayList<>();
        int i = 1;
        String prev = null;
        for (Map<String, Object> f : selected) {
            CognitiveFrameworkDefinition def = registry.get(str(f.get("frameworkId")), str(f.get("version")));
            Map<String, Object> step = new LinkedHashMap<>();
            String stepId = "step-" + i;
            step.put("stepId", stepId);
            step.put("frameworkRef", Map.of("frameworkId", def.getFrameworkId(), "version", def.getVersion()));
            step.put("dependsOn", prev == null ? List.of() : List.of(prev));
            step.put("inputSchemaRef", def.getInputSchemaRef());
            step.put("outputSchemaRef", def.getOutputSchemaRef());
            step.put("requiredCapabilities", nullSafe(def.getRequiredCapabilities()));
            step.put("estimatedTokens", 500);
            step.put("allowSideEffects", false);
            step.put("stopConditions", nullSafe(def.getStopConditions()));
            steps.add(step);
            stopConditions.addAll(nullSafe(def.getStopConditions()));
            prev = stepId;
            i++;
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planId", "plan-" + java.util.UUID.randomUUID());
        plan.put("requestId", req.get("requestId"));
        plan.put("scenarioId", req.get("scenarioId"));
        plan.put("blueprintId", req.get("blueprintId"));
        plan.put("blueprintVersion", req.get("blueprintVersion"));
        plan.put("contextDigest", req.get("contextDigest"));
        plan.put("steps", steps);
        plan.put("stopConditions", stopConditions.stream().distinct().toList());
        plan.put("budget", budget);
        plan.put("availableCapabilities", new ArrayList<>(availableCapabilities));
        plan.put("authorized", !Boolean.FALSE.equals(req.get("authorized")));
        plan.put("sideEffectAuthorized", false);
        return plan;
    }

    private static int score(CognitiveFrameworkDefinition f, String problemType, String domain,
                             Set<String> presentInputs, Set<String> availableCapabilities) {
        int score = 0;
        if (!problemType.isBlank() && f.getProblemTypes() != null && f.getProblemTypes().contains(problemType)) {
            score += 40;
        }
        if (!domain.isBlank() && f.getDomains() != null && f.getDomains().contains(domain)) {
            score += 20;
        }
        List<String> pre = nullSafe(f.getPreconditions());
        if (!pre.isEmpty() && presentInputs.containsAll(pre)) {
            score += 20;
        }
        List<String> caps = nullSafe(f.getRequiredCapabilities());
        if (caps.isEmpty() || availableCapabilities.containsAll(caps)) {
            score += 10;
        }
        if (f.isContractResolvable()) {
            score += 10;
        }
        int steps = f.getSteps() == null ? 0 : f.getSteps().size();
        return score - steps * 2;
    }

    private static boolean riskApprovalRequired(CognitiveFrameworkDefinition f) {
        Object v = f.getGovernance() == null ? null : f.getGovernance().get("productionApprovalRequired");
        return Boolean.TRUE.equals(v);
    }

    private static Map<String, Object> riskDecision(String riskLevel, String riskApproval) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("riskLevel", riskLevel == null || riskLevel.isBlank() ? "UNSPECIFIED" : riskLevel);
        m.put("riskApproval", riskApproval == null || riskApproval.isBlank() ? "NOT_PROVIDED" : riskApproval);
        m.put("requiresHumanApproval", "HIGH".equalsIgnoreCase(riskLevel)
                && !"APPROVED".equalsIgnoreCase(riskApproval));
        return m;
    }

    private static Map<String, Object> trace(String problemType, String domain, int candidateCount,
                                             List<Map<String, Object>> selected,
                                             List<Map<String, Object>> excluded) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("problemType", problemType);
        m.put("domain", domain);
        m.put("candidateCount", candidateCount);
        m.put("eligibleCount", selected.size());
        m.put("excludedCount", excluded.size());
        m.put("ranking", "DETERMINISTIC_ORDINAL_ONLY");
        m.put("probabilitySemantics", "NONE");
        return m;
    }

    private static List<String> collectStop(List<Map<String, Object>> selected) {
        return List.of("require_evidence_refs", "require_validation", "require_schema_validation");
    }

    private static List<String> nullSafe(List<String> in) {
        return in == null ? List.of() : in;
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object raw) {
        return raw instanceof Map ? (Map<String, Object>) raw : Map.of();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int intOf(Object o, int fallback) {
        return o instanceof Number n ? n.intValue() : fallback;
    }
}
