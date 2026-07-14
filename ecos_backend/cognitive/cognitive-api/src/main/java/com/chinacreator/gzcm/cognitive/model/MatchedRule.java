package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.List;

/**
 * 规则匹配结果。
 */
public class MatchedRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则 ID */
    private String ruleId;
    /** 规则名称 */
    private String ruleName;
    /** 规则描述 */
    private String description;
    /** 匹配置信度 0~1 */
    private Double confidence;
    /** 触发的动作列表 */
    private List<Action> actions;

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public List<Action> getActions() { return actions; }
    public void setActions(List<Action> actions) { this.actions = actions; }
}
