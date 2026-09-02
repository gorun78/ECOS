package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.Map;

/**
 * 推理步骤 — 5 类推理可解释契约之一。
 *
 * <p>既有字段保持不删，新增 ruleRef / precedentRef / sourceType / stepIndex
 * 对齐 ECOS-DESIGN-COG-04 §三。</p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-01 (PMO-35), 2026-09-02 (Wave-2C 增量)
 */
public class ReasoningStep {

    // ── 既有字段 (PMO-35) ──
    private String stepId;
    private String description;
    private String ruleApplied;
    private Map<String, Object> inputFacts;
    private Object outputFact;
    private double confidence;

    // ── 新增字段 (Wave-2C, 对齐 04 文档 §三) ──
    /** 规则引用（可空，KG 步骤为 null） */
    private RuleRef ruleRef;
    /** 先例引用（Wave-3.2 新增，PRECEDENT 步骤使用） */
    private PrecedentRef precedentRef;
    /** 来源类型: KG / RULE / RAG / LLM / PRECEDENT */
    private String sourceType;
    /** 层号（diagnose 因果拓扑排序），0 表示未排序 */
    private int stepIndex;

    public ReasoningStep() {
    }

    public ReasoningStep(String stepId, String description, String ruleApplied,
                         Map<String, Object> inputFacts, Object outputFact, double confidence) {
        this.stepId = stepId;
        this.description = description;
        this.ruleApplied = ruleApplied;
        this.inputFacts = inputFacts;
        this.outputFact = outputFact;
        this.confidence = confidence;
    }

    // ── 既有 getter / setter ──

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRuleApplied() { return ruleApplied; }
    public void setRuleApplied(String ruleApplied) { this.ruleApplied = ruleApplied; }

    public Map<String, Object> getInputFacts() { return inputFacts; }
    public void setInputFacts(Map<String, Object> inputFacts) { this.inputFacts = inputFacts; }

    public Object getOutputFact() { return outputFact; }
    public void setOutputFact(Object outputFact) { this.outputFact = outputFact; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    // ── 新增 getter / setter ──

    public RuleRef getRuleRef() { return ruleRef; }
    public void setRuleRef(RuleRef ruleRef) { this.ruleRef = ruleRef; }

    public PrecedentRef getPrecedentRef() { return precedentRef; }
    public void setPrecedentRef(PrecedentRef precedentRef) { this.precedentRef = precedentRef; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public int getStepIndex() { return stepIndex; }
    public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }
}
