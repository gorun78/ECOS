package com.chinacreator.gzcm.engine.cognitive2.model;

/**
 * 因果链节点 — 代表因果图中的一条合规规则。
 */
public class CausalChainNode {

    private String id;
    private String ruleId;
    private String ruleName;
    private String domain;
    private String description;

    public CausalChainNode() {}

    public CausalChainNode(String id, String ruleId, String ruleName, String domain, String description) {
        this.id = id;
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.domain = domain;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
