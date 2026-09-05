package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.List;

/**
 * 推理路径 — 5 类推理可解释契约之一。
 *
 * <p>既有字段保持不删，新增 ruleRefs 列表
 * 对齐 ECOS-DESIGN-COG-04 §三。</p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-01 (PMO-35), 2026-09-02 (Wave-2C 增量)
 */
public class ReasoningPath {

    // ── 既有字段 (PMO-35) ──
    private List<ReasoningStep> steps;
    private String conclusion;
    private String justification;

    // ── 新增字段 (Wave-2C, 对齐 04 文档 §三) ──
    /** 本路径引用的所有规则（从 steps 中聚合） */
    private List<RuleRef> ruleRefs;

    // ── 新增字段 (Wave-3.2, 对齐 04 文档 §三) ──
    /** 本路径引用的所有先例（从 steps 中聚合，PRECEDENT 步骤携带） */
    private List<PrecedentRef> precedentRefs;
    /** 结构化解释子条款（多段，对齐 04 文档 §三 #3） */
    private List<JustificationClause> clauses;

    public ReasoningPath() {
    }

    public ReasoningPath(List<ReasoningStep> steps, String conclusion, String justification) {
        this.steps = steps;
        this.conclusion = conclusion;
        this.justification = justification;
    }

    // ── 既有 getter / setter ──

    public List<ReasoningStep> getSteps() { return steps; }
    public void setSteps(List<ReasoningStep> steps) { this.steps = steps; }

    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }

    // ── 新增 getter / setter ──

    public List<RuleRef> getRuleRefs() { return ruleRefs; }
    public void setRuleRefs(List<RuleRef> ruleRefs) { this.ruleRefs = ruleRefs; }

    public List<PrecedentRef> getPrecedentRefs() { return precedentRefs; }
    public void setPrecedentRefs(List<PrecedentRef> precedentRefs) { this.precedentRefs = precedentRefs; }

    public List<JustificationClause> getClauses() { return clauses; }
    public void setClauses(List<JustificationClause> clauses) { this.clauses = clauses; }
}
