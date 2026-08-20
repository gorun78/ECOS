package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningStep;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 推理路径构建器 — 从规则裁决结果生成结构化的逐步推理路径。
 *
 * <p>对齐 Semantica explanation_generator，把规则裁决的 reasoningChain
 * 升级为 ReasoningStep/ReasoningPath/Justification 三件套。</p>
 */
@Service
public class ReasoningPathBuilder {

    /**
     * 从规则裁决结果构建推理路径。
     *
     * @param ruleId        规则 ID
     * @param ruleName      规则名称
     * @param condition     规则条件（SpEL 或纯文本）
     * @param action        规则动作
     * @param facts         输入事实
     * @param evalResult    SpEL 评估结果
     * @param stepIndex     步骤序号
     * @return 单步推理步骤
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
        return step;
    }

    /**
     * 构建完整推理路径。
     *
     * @param steps      所有步骤
     * @param conclusion 最终结论
     * @return 推理路径
     */
    public ReasoningPath buildPath(List<ReasoningStep> steps, String conclusion) {
        ReasoningPath path = new ReasoningPath();
        path.setSteps(steps);
        path.setConclusion(conclusion);

        // 证成说明：为什么这么判
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

        return path;
    }
}
