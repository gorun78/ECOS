package com.chinacreator.gzcm.engine.cognitive2.model;

/**
 * 规则引用 — 5 类推理可解释契约之一。
 *
 * <p>指向"用了什么规则"，供前端点击跳转至合规规则详情。
 * 与 ReasoningStep.ruleApplied (string) 兼容并存：
 * 渲染层按规则 {@code ruleRef != null ? ruleRef.ruleName : ruleApplied}。</p>
 *
 * <p>对齐文档: ECOS-DESIGN-COG-04 §三</p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02
 */
public class RuleRef {

    /** sys_compliance_rule.id */
    private String ruleId;
    /** 规则名称 */
    private String ruleName;
    /** 原 condition（SpEL 或自然语言） */
    private String condition;
    /** 原 action 文本 */
    private String action;
    /** 命中分类（compliance / business / oag） */
    private String category;
    /** 规则版本（kb-engine 提供） */
    private String version;
    /** 在规则命中列表中的排序（0=最优先），来源 extraction_drafts.draft_rule_rank 或默认 -1 */
    private int sourceRank;

    public RuleRef() {
    }

    public RuleRef(String ruleId, String ruleName, String condition, String action) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.condition = condition;
        this.action = action;
        this.category = "compliance";
        this.sourceRank = -1;
    }

    // ── getter / setter ──

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public int getSourceRank() { return sourceRank; }
    public void setSourceRank(int sourceRank) { this.sourceRank = sourceRank; }
}
