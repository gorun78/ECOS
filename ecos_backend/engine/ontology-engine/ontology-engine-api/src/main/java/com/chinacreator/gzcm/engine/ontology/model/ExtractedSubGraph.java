package com.chinacreator.gzcm.engine.ontology.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** KAG核心模型：一次抽取产出的子图（实体+关系+规则） */
public class ExtractedSubGraph {

    private List<ExtractedEntity> entities = new ArrayList<>();
    private List<ExtractedRelation> relations = new ArrayList<>();
    private List<ExtractedRule> rules = new ArrayList<>();

    public ExtractedSubGraph() {}

    public List<ExtractedEntity> getEntities() { return entities; }
    public void setEntities(List<ExtractedEntity> entities) { this.entities = entities; }

    public List<ExtractedRelation> getRelations() { return relations; }
    public void setRelations(List<ExtractedRelation> relations) { this.relations = relations; }

    public List<ExtractedRule> getRules() { return rules; }
    public void setRules(List<ExtractedRule> rules) { this.rules = rules; }

    // ── Inner types ──

    public static class ExtractedEntity {
        private String name;
        private String type;
        private Map<String, Object> properties;
        private double confidence;

        public ExtractedEntity() {}
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Map<String, Object> getProperties() { return properties; }
        public void setProperties(Map<String, Object> properties) { this.properties = properties; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }

    public static class ExtractedRelation {
        private String sourceEntity;
        private String targetEntity;
        private String relationType;
        private double confidence;

        public ExtractedRelation() {}
        public String getSourceEntity() { return sourceEntity; }
        public void setSourceEntity(String sourceEntity) { this.sourceEntity = sourceEntity; }
        public String getTargetEntity() { return targetEntity; }
        public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }
        public String getRelationType() { return relationType; }
        public void setRelationType(String relationType) { this.relationType = relationType; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }

    public static class ExtractedRule {
        private String name;
        private String domain;
        private String condition;   // SpEL预备表达式
        private String action;
        private List<String> applicableObjectTypes;
        private double confidence;
        private String sourceExcerpt;

        public ExtractedRule() {}
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public List<String> getApplicableObjectTypes() { return applicableObjectTypes; }
        public void setApplicableObjectTypes(List<String> applicableObjectTypes) { this.applicableObjectTypes = applicableObjectTypes; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public String getSourceExcerpt() { return sourceExcerpt; }
        public void setSourceExcerpt(String sourceExcerpt) { this.sourceExcerpt = sourceExcerpt; }
    }
}
