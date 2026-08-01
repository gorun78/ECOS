package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则影响分析结果 — 找出所有受该规则变更影响的下游规则。
 */
public class ImpactAnalysisResult {

    private String sourceRuleId;
    private String sourceRuleName;
    private List<ImpactedRule> impactedRules;

    public ImpactAnalysisResult() {
        this.impactedRules = new ArrayList<>();
    }

    public ImpactAnalysisResult(String sourceRuleId, String sourceRuleName) {
        this();
        this.sourceRuleId = sourceRuleId;
        this.sourceRuleName = sourceRuleName;
    }

    public String getSourceRuleId() { return sourceRuleId; }
    public void setSourceRuleId(String sourceRuleId) { this.sourceRuleId = sourceRuleId; }

    public String getSourceRuleName() { return sourceRuleName; }
    public void setSourceRuleName(String sourceRuleName) { this.sourceRuleName = sourceRuleName; }

    public List<ImpactedRule> getImpactedRules() { return impactedRules; }
    public void setImpactedRules(List<ImpactedRule> impactedRules) { this.impactedRules = impactedRules; }

    /**
     * 受影响规则条目。
     */
    public static class ImpactedRule {
        private String ruleId;
        private String ruleName;
        private String domain;
        private String matchReason;

        public ImpactedRule() {}

        public ImpactedRule(String ruleId, String ruleName, String domain, String matchReason) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.domain = domain;
            this.matchReason = matchReason;
        }

        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }

        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }

        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }

        public String getMatchReason() { return matchReason; }
        public void setMatchReason(String matchReason) { this.matchReason = matchReason; }
    }
}
