package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 解释子条款 — Justification 结构化的最小单元（Wave-3.2 增量，对齐 04 文档 §三 #3）。
 *
 * <p>每个条款对应 ReasoningPath 中的某一步推理，携带：
 * <ul>
 *   <li>clauseId   — 唯一标识</li>
 *   <li>clauseType — FACT_ACCRUAL / RULE_TRIGGER / PRECEDENT_RECALL / COUNTER_EVIDENCE</li>
 *   <li>stepRef    — 关联的步骤 ID（ReasoningStep.stepId）</li>
 *   <li>text       — 渲染文本（i18n key 或 plain）</li>
 *   <li>i18nKey    — 前端 i18n key（可选，供 t() 查）</li>
 *   <li>factRefs   — 引用的事实 key 列表</li>
 *   <li>weight     — 该条款在结论的权重 0~1</li>
 * </ul>
 *
 * <p>对齐文档: ECOS-DESIGN-COG-04 §三</p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-3.2)
 */
public class JustificationClause {

    /** 事实累计 */
    public static final String TYPE_FACT_ACCRUAL      = "FACT_ACCRUAL";
    /** 规则命中 */
    public static final String TYPE_RULE_TRIGGER      = "RULE_TRIGGER";
    /** 先例召回 */
    public static final String TYPE_PRECEDENT_RECALL  = "PRECEDENT_RECALL";
    /** 反证 */
    public static final String TYPE_COUNTER_EVIDENCE  = "COUNTER_EVIDENCE";

    private String clauseId;
    /** 条款类型（FACT_ACCRUAL / RULE_TRIGGER / PRECEDENT_RECALL / COUNTER_EVIDENCE） */
    private String clauseType;
    /** 关联的 stepId */
    private String stepRef;
    /** 渲染文本 */
    private String text;
    /** i18n key（前端用 t() 查） */
    private String i18nKey;
    /** 引用的事实 key */
    private List<String> factRefs;
    /** 权重 0~1 */
    private double weight;

    public JustificationClause() {
        this.factRefs = new ArrayList<>();
    }

    public JustificationClause(String clauseId, String clauseType, String stepRef,
                               String text, double weight) {
        this();
        this.clauseId = clauseId;
        this.clauseType = clauseType;
        this.stepRef = stepRef;
        this.text = text;
        this.weight = weight;
    }

    public String getClauseId() { return clauseId; }
    public void setClauseId(String clauseId) { this.clauseId = clauseId; }

    public String getClauseType() { return clauseType; }
    public void setClauseType(String clauseType) { this.clauseType = clauseType; }

    public String getStepRef() { return stepRef; }
    public void setStepRef(String stepRef) { this.stepRef = stepRef; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getI18nKey() { return i18nKey; }
    public void setI18nKey(String i18nKey) { this.i18nKey = i18nKey; }

    public List<String> getFactRefs() { return factRefs; }
    public void setFactRefs(List<String> factRefs) {
        this.factRefs = factRefs != null ? factRefs : new ArrayList<>();
    }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
}
