package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningStep;
import com.chinacreator.gzcm.engine.cognitive2.model.RuleRef;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推理路径构建器 — 从规则裁决结果/因果链生成结构化的逐步推理路径。
 *
 * <p>对齐 Semantica explanation_generator 和 ECOS-DESIGN-COG-04 §三/§四：
 * 把规则裁决的 reasoningChain 升级为 ReasoningStep/ReasoningPath/Justification 三件套，
 * 每个 RULE 步骤至少携带 1 个 RuleRef（G2 验收硬指标）。</p>
 *
 * <p>Wave-2C 增量：
 * <ul>
 *   <li>buildStep 新增 RuleRef 设值 + sourceType/stepIndex</li>
 *   <li>buildPath 聚合 ruleRefs 到 ReasoningPath</li>
 *   <li>buildStepFromCausal: 因果链节点 → 推理步骤映射（04 文档 §4.1）</li>
 * </ul>
 * </p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-01 (PMO-35), 2026-09-02 (Wave-2C 增量)
 */
@Service
public class ReasoningPathBuilder {

    private static final Logger log = LoggerFactory.getLogger(ReasoningPathBuilder.class);

    /**
     * 从规则裁决结果构建推理步骤。
     *
     * @param ruleId        规则 ID
     * @param ruleName      规则名称
     * @param condition     规则条件（SpEL 或纯文本）
     * @param action        规则动作
     * @param facts         输入事实
     * @param evalResult    SpEL 评估结果
     * @param stepIndex     步骤序号
     * @return 单步推理步骤（含 RuleRef）
     */
    public ReasoningStep buildStep(String ruleId, String ruleName, String condition,
                                   String action, Map<String, Object> facts,
                                   SpelConditionEvaluator.EvalResult evalResult, int stepIndex) {
        ReasoningStep step = new ReasoningStep();
        step.setStepId("step-" + stepIndex);
        step.setRuleApplied(ruleName != null ? ruleName : ruleId);
        step.setInputFacts(facts != null ? new LinkedHashMap<>(facts) : new HashMap<>());

        // 输出结论
        String output = evalResult.isSatisfied()
            ? "Rule satisfied → action: " + (action != null ? action : "N/A")
            : "Rule not satisfied";
        step.setOutputFact(output);

        // 描述
        step.setDescription(String.format("Rule [%s] condition '%s' evaluated %s. %s",
            ruleName, condition, evalResult.isSatisfied() ? "TRUE" : "FALSE",
            evalResult.getDetail() != null ? evalResult.getDetail() : ""));

        // 置信度
        step.setConfidence(evalResult.isSatisfied() ? 0.85 : 0.3);

        // ── Wave-2C 增量: RuleRef (04 文档 G2) ──
        RuleRef ruleRef = new RuleRef(ruleId, ruleName, condition, action);
        ruleRef.setSourceRank(0);
        step.setRuleRef(ruleRef);
        step.setSourceType("RULE");
        step.setStepIndex(stepIndex + 1);

        return step;
    }

    /**
     * 从 ComplianceRule 对象直接构建推理步骤（便捷重载）。
     *
     * @param rule        合规规则
     * @param facts       输入事实
     * @param evalResult  SpEL 评估结果
     * @param stepIndex   步骤序号
     * @return 推理步骤（含完整 RuleRef）
     */
    public ReasoningStep buildStep(ComplianceRule rule, Map<String, Object> facts,
                                   SpelConditionEvaluator.EvalResult evalResult, int stepIndex) {
        ReasoningStep step = buildStep(
            rule.getId(), rule.getName(), rule.getCondition(),
            rule.getAction(), facts, evalResult, stepIndex);
        // 补充 category 和 version
        if (step.getRuleRef() != null) {
            step.getRuleRef().setCategory(rule.getDomain() != null ? rule.getDomain() : "compliance");
            if (rule.getVersion() > 0) {
                step.getRuleRef().setVersion(String.valueOf(rule.getVersion()));
            }
        }
        return step;
    }

    /**
     * 构建完整推理路径。
     *
     * <p>Wave-2C: 聚合 steps 中的 ruleRefs 到 path.ruleRefs，
     * justification 保留字符串摘要 + 结构化计数。</p>
     *
     * @param steps      所有步骤
     * @param conclusion 最终结论
     * @return 推理路径（含 ruleRefs 列表）
     */
    public ReasoningPath buildPath(List<ReasoningStep> steps, String conclusion) {
        ReasoningPath path = new ReasoningPath();
        path.setSteps(steps);
        path.setConclusion(conclusion);

        // 证成说明
        StringBuilder just = new StringBuilder();
        int satisfiedCount = 0;
        for (ReasoningStep s : steps) {
            if (s.getOutputFact() != null && s.getOutputFact().toString().contains("satisfied")) {
                satisfiedCount++;
            }
        }
        just.append(String.format("Evaluated %d rules, %d matched. ", steps.size(), satisfiedCount));
        just.append("Conclusion: ").append(conclusion);
        path.setJustification(just.toString());

        // ── Wave-2C 增量: 聚合 ruleRefs ──
        List<RuleRef> ruleRefs = steps.stream()
            .map(ReasoningStep::getRuleRef)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        path.setRuleRefs(ruleRefs);

        return path;
    }

    /**
     * 因果链节点 → 推理步骤映射（04 文档 §4.1）。
     *
     * <p>每个 CausalChainNode 展开为 1 个 ReasoningStep：
     * <ul>
     *   <li>stepId = "depth-{depth}"</li>
     *   <li>sourceType = node.getSource() ∈ {KG, LLM, RULE}</li>
     *   <li>source=RULE 且 ruleId 非空 → 设 ruleRef</li>
     * </ul>
     *
     * @param nodes 因果链节点列表（按 depth 排序）
     * @return 推理步骤列表
     */
    public List<ReasoningStep> buildStepsFromCausal(List<CausalChainNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        return nodes.stream().map(node -> {
            ReasoningStep s = new ReasoningStep();
            s.setStepId("depth-" + node.getDepth());
            s.setStepIndex(node.getDepth());
            s.setDescription(node.getDescription());
            s.setInputFacts(Collections.emptyMap());
            s.setOutputFact(node.getNode());
            s.setConfidence(node.getConfidence());
            s.setSourceType(node.getSource() != null ? node.getSource().toUpperCase() : "KG");

            // source=RULE 且 ruleId 非空 → ruleRef
            if ("RULE".equalsIgnoreCase(node.getSource()) && node.getRuleId() != null) {
                RuleRef ref = new RuleRef(
                    node.getRuleId(),
                    node.getRuleName() != null ? node.getRuleName() : "rule-" + node.getRuleId(),
                    null, null);
                ref.setCategory(node.getDomain() != null ? node.getDomain() : "compliance");
                s.setRuleRef(ref);
            }
            return s;
        }).collect(Collectors.toList());
    }

    /**
     * 因果链 → 完整推理路径（04 文档 §4.1）。
     *
     * @param nodes      因果链节点列表
     * @param conclusion 最终诊断结论
     * @return 推理路径
     */
    public ReasoningPath buildPathFromCausal(List<CausalChainNode> nodes, String conclusion) {
        List<ReasoningStep> steps = buildStepsFromCausal(nodes);
        ReasoningPath path = new ReasoningPath();
        path.setSteps(steps);
        path.setConclusion(conclusion);

        // 结构化 justification：每层来源 + 置信度
        StringBuilder just = new StringBuilder();
        just.append("因果链 ").append(nodes.size()).append(" 层: ");
        for (CausalChainNode node : nodes) {
            just.append("[depth=")
                .append(node.getDepth())
                .append(" source=")
                .append(node.getSource() != null ? node.getSource() : "KG")
                .append(" conf=")
                .append(String.format("%.2f", node.getConfidence()))
                .append("] ");
        }
        just.append("Conclusion: ").append(conclusion);
        path.setJustification(just.toString());

        // 聚合 ruleRefs
        List<RuleRef> ruleRefs = steps.stream()
            .map(ReasoningStep::getRuleRef)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        path.setRuleRefs(ruleRefs);

        return path;
    }
}
