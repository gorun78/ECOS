package com.chinacreator.gzcm.sysman.security.policy.model;

import java.util.List;
import java.util.Map;

/**
 * 数据分级分类策略模型
 * 用于解析和构建数据分级分类策略配置
 */
public class DataClassificationPolicy {
    
    /**
     * 分类规则列表
     */
    private List<ClassificationRule> classificationRules;
    
    /**
     * 分级标准
     */
    private Map<String, ClassificationLevel> levels;
    
    /**
     * 自动分类规则（正则表达式、关键词等）
     */
    private List<AutoClassificationRule> autoRules;
    
    /**
     * 分类规则
     */
    public static class ClassificationRule {
        private String ruleId;
        private String ruleName;
        private String resourcePattern;  // 资源匹配模式（表名、字段名等）
        private String dataType;  // 数据类型（PERSONAL, BUSINESS, PUBLIC等）
        private String level;  // 分级（PUBLIC, INTERNAL, SECRET, CONFIDENTIAL等）
        private Map<String, Object> conditions;  // 条件（JSON格式）
        
        // Getters and Setters
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getResourcePattern() { return resourcePattern; }
        public void setResourcePattern(String resourcePattern) { this.resourcePattern = resourcePattern; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public Map<String, Object> getConditions() { return conditions; }
        public void setConditions(Map<String, Object> conditions) { this.conditions = conditions; }
    }
    
    /**
     * 分级标准
     */
    public static class ClassificationLevel {
        private String levelCode;
        private String levelName;
        private Integer levelValue;  // 数值，越大越敏感
        private String description;
        private Map<String, Object> securityRequirements;  // 安全要求（加密、访问控制等）
        
        // Getters and Setters
        public String getLevelCode() { return levelCode; }
        public void setLevelCode(String levelCode) { this.levelCode = levelCode; }
        public String getLevelName() { return levelName; }
        public void setLevelName(String levelName) { this.levelName = levelName; }
        public Integer getLevelValue() { return levelValue; }
        public void setLevelValue(Integer levelValue) { this.levelValue = levelValue; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Object> getSecurityRequirements() { return securityRequirements; }
        public void setSecurityRequirements(Map<String, Object> securityRequirements) { this.securityRequirements = securityRequirements; }
    }
    
    /**
     * 自动分类规则
     */
    public static class AutoClassificationRule {
        private String ruleId;
        private String ruleName;
        private String pattern;  // 正则表达式或关键词
        private String patternType;  // REGEX, KEYWORD, ML_MODEL
        private String dataType;
        private String level;
        private Double confidence;  // 置信度（ML模型使用）
        
        // Getters and Setters
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public String getPatternType() { return patternType; }
        public void setPatternType(String patternType) { this.patternType = patternType; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
    }
    
    // Getters and Setters
    public List<ClassificationRule> getClassificationRules() {
        return classificationRules;
    }
    
    public void setClassificationRules(List<ClassificationRule> classificationRules) {
        this.classificationRules = classificationRules;
    }
    
    public Map<String, ClassificationLevel> getLevels() {
        return levels;
    }
    
    public void setLevels(Map<String, ClassificationLevel> levels) {
        this.levels = levels;
    }
    
    public List<AutoClassificationRule> getAutoRules() {
        return autoRules;
    }
    
    public void setAutoRules(List<AutoClassificationRule> autoRules) {
        this.autoRules = autoRules;
    }
}

