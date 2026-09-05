package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 先例引用 — 5 类推理可解释契约之一（Wave-3.2 增量，对齐 04 文档 §三 #2）。
 *
 * <p>指向"基于什么先例"：引用一条历史决策（ecos_decision.id），
 * 携带相似度证据，供前端点击跳转至历史决策详情。</p>
 *
 * <p>与 RuleRef 区别：RuleRef 指向"用了什么规则"（sys_compliance_rule），
 * PrecedentRef 指向"基于什么先例"（ecos_decision）。
 * 两者在 ReasoningStep 中并存：
 * <ul>
 *   <li>KG/RULE 步骤携带 ruleRef</li>
 *   <li>PRECEDENT 步骤携带 precedentRef</li>
 * </ul>
 *
 * <p>对齐文档: ECOS-DESIGN-COG-04 §三</p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-3.2)
 */
public class PrecedentRef {

    /** ecos_decision_precedent.id 或 ecos_decision.id（PMO-32） */
    private String precedentId;
    /** 出处决策 id */
    private String decisionId;
    /** 案例摘要 */
    private String summary;
    /** 历史结果（outcome） */
    private String outcome;
    /** 相似度 0~1（pgvector 向量距离，不可用时 -1） */
    private double similarity;
    /** 相似度证据（vector_score / category / scenario_len_diff 等） */
    private Map<String, Object> similarityEvidence;

    public PrecedentRef() {
        this.similarity = -1.0;
        this.similarityEvidence = new LinkedHashMap<>();
    }

    public PrecedentRef(String precedentId, String decisionId, String summary,
                        String outcome, double similarity) {
        this();
        this.precedentId = precedentId;
        this.decisionId = decisionId;
        this.summary = summary;
        this.outcome = outcome;
        this.similarity = similarity;
    }

    public String getPrecedentId() { return precedentId; }
    public void setPrecedentId(String precedentId) { this.precedentId = precedentId; }

    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public double getSimilarity() { return similarity; }
    public void setSimilarity(double similarity) { this.similarity = similarity; }

    public Map<String, Object> getSimilarityEvidence() { return similarityEvidence; }
    public void setSimilarityEvidence(Map<String, Object> similarityEvidence) {
        this.similarityEvidence = similarityEvidence;
    }

    /**
     * 便捷工厂：从 Decision 对象构建（Wave-3.2 T2）。
     *
     * @param decision   历史决策
     * @param similarity 相似度（向量分数，无向量时填 -1）
     * @return PrecedentRef
     */
    public static PrecedentRef fromDecision(com.chinacreator.gzcm.engine.cognitive2.model.Decision decision,
                                            double similarity) {
        PrecedentRef ref = new PrecedentRef();
        if (decision != null) {
            ref.setPrecedentId(decision.getId());
            ref.setDecisionId(decision.getId());
            String scenario = decision.getScenario() != null ? decision.getScenario() : "";
            String reasoning = decision.getReasoning() != null ? decision.getReasoning() : "";
            ref.setSummary((scenario + " | " + reasoning).trim());
            ref.setOutcome(decision.getOutcome() != null ? decision.getOutcome() : "");
        }
        ref.setSimilarity(similarity);
        return ref;
    }
}
