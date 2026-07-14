package com.chinacreator.gzcm.sysman.security.policy.model;

import java.util.List;
import java.util.Map;

/**
 * 数据泄露防护（DLP）策略模型
 * 用于解析和构建DLP策略配置
 */
public class DLPPolicy {
    
    /**
     * 敏感数据识别规则
     */
    private List<SensitiveDataRule> sensitiveDataRules;
    
    /**
     * 泄露检测规则
     */
    private List<LeakDetectionRule> leakDetectionRules;
    
    /**
     * 阻断规则
     */
    private List<BlockRule> blockRules;
    
    /**
     * 告警规则
     */
    private List<AlertRule> alertRules;
    
    /**
     * 敏感数据识别规则
     */
    public static class SensitiveDataRule {
        private String ruleId;
        private String ruleName;
        private String dataType;  // ID_CARD, PHONE, EMAIL, BANK_CARD等
        private String pattern;  // 正则表达式
        private String patternType;  // REGEX, KEYWORD, ML_MODEL
        private Integer minConfidence;  // 最小置信度（0-100）
        private Map<String, Object> metadata;  // 元数据（字段名、表名等）
        
        // Getters and Setters
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public String getPatternType() { return patternType; }
        public void setPatternType(String patternType) { this.patternType = patternType; }
        public Integer getMinConfidence() { return minConfidence; }
        public void setMinConfidence(Integer minConfidence) { this.minConfidence = minConfidence; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * 泄露检测规则
     */
    public static class LeakDetectionRule {
        private String ruleId;
        private String ruleName;
        private String detectionType;  // EXPORT, API_CALL, EMAIL, USB等
        private String condition;  // 检测条件（JSON格式）
        private Integer threshold;  // 阈值（如：单次导出超过1000条）
        private String action;  // ALERT, BLOCK, AUDIT
        
        // Getters and Setters
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getDetectionType() { return detectionType; }
        public void setDetectionType(String detectionType) { this.detectionType = detectionType; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public Integer getThreshold() { return threshold; }
        public void setThreshold(Integer threshold) { this.threshold = threshold; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }
    
    /**
     * 阻断规则
     */
    public static class BlockRule {
        private String ruleId;
        private String ruleName;
        private String blockType;  // EXPORT, API_CALL, EMAIL等
        private String condition;  // 阻断条件（JSON格式）
        private String reason;  // 阻断原因
        
        // Getters and Setters
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getBlockType() { return blockType; }
        public void setBlockType(String blockType) { this.blockType = blockType; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    /**
     * 告警规则
     */
    public static class AlertRule {
        private String ruleId;
        private String ruleName;
        private String alertType;  // EMAIL, SMS, SYSTEM, WEBHOOK
        private String condition;  // 告警条件（JSON格式）
        private List<String> recipients;  // 接收人列表
        private String template;  // 告警模板
        
        // Getters and Setters
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getAlertType() { return alertType; }
        public void setAlertType(String alertType) { this.alertType = alertType; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public List<String> getRecipients() { return recipients; }
        public void setRecipients(List<String> recipients) { this.recipients = recipients; }
        public String getTemplate() { return template; }
        public void setTemplate(String template) { this.template = template; }
    }
    
    // Getters and Setters
    public List<SensitiveDataRule> getSensitiveDataRules() {
        return sensitiveDataRules;
    }
    
    public void setSensitiveDataRules(List<SensitiveDataRule> sensitiveDataRules) {
        this.sensitiveDataRules = sensitiveDataRules;
    }
    
    public List<LeakDetectionRule> getLeakDetectionRules() {
        return leakDetectionRules;
    }
    
    public void setLeakDetectionRules(List<LeakDetectionRule> leakDetectionRules) {
        this.leakDetectionRules = leakDetectionRules;
    }
    
    public List<BlockRule> getBlockRules() {
        return blockRules;
    }
    
    public void setBlockRules(List<BlockRule> blockRules) {
        this.blockRules = blockRules;
    }
    
    public List<AlertRule> getAlertRules() {
        return alertRules;
    }
    
    public void setAlertRules(List<AlertRule> alertRules) {
        this.alertRules = alertRules;
    }
}

