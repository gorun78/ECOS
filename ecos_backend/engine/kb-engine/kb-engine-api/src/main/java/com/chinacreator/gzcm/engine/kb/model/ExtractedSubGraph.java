package com.chinacreator.gzcm.engine.kb.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KB 本地抽取子图 — F04-18 契约隔离。
 *
 * <p>kb-engine 原先直接 import {@code com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph}
 * 的 {@code ExtractedEntity}/{@code ExtractedRelation}/{@code ExtractedRule} 三个内嵌类型，
 * 形成 kb(api)→ontology 跨引擎内聚耦合。本类为 kb 域<b>自有的值对象镜像</b>，
 * 字段与 ontology 侧对齐（name/type/properties/confidence；source/target/relationType；
 * name/domain/condition/action/sourceExcerpt），但<b>不依赖</b> ontology 任何类，
 * 使 {@code kb-engine-api} 达到 0 处 {@code engine.ontology} import。
 *
 * <p>命名刻意与 ontology 侧同形（ExtractedEntity 等）以最小化调用方改动；
 * FQCN 不同（ontology 在 {@code engine.ontology.model}，本类在 {@code engine.kb.model}），
 * 二者不可互换，调用方须使用本 kb 类型。</p>
 */
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

    /** KB 本地抽取实体（name + type + properties + confidence）。 */
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

    /** KB 本地抽取关系（source/target + relationType + confidence）。 */
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

    /** KB 本地抽取规则（name/domain/condition/action/sourceExcerpt 等）。 */
    public static class ExtractedRule {
        private String name;
        private String domain;
        /** SpEL 预备表达式 */
        private String condition;
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
