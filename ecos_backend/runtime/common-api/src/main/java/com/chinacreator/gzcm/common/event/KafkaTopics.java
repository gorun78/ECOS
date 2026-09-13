package com.chinacreator.gzcm.common.event;

public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String IDENTITY = "ecos.identity";
    public static final String CATALOG = "ecos.catalog";
    public static final String ONTOLOGY = "ecos.ontology";
    /** PMO-50 T4: 本体版本发布专用 topic — buszhi → dBpe 跨 JVM 事件 (兼容 @EventListener Spring 内存路径)。
     *  与 {@link #ONTOLOGY} 通用主题区分, 消费侧 groupId=dccheng-ontology-consumer, 避免多业务共用。 */
    public static final String ONTOLOGY_PUBLISHED = "ecos.ontology.published";
    public static final String OBJECT = "ecos.object";
    public static final String WORKFLOW = "ecos.workflow";
    public static final String AGENT = "ecos.agent";
    public static final String KNOWLEDGE = "ecos.knowledge";
    public static final String AUDIT = "ecos.audit";

    public static String topicForAggregate(String aggregateType) {
        if (aggregateType == null) return AUDIT;
        return switch (aggregateType.toLowerCase()) {
            case "user", "role", "permission", "organization", "tenant" -> IDENTITY;
            case "dataset", "datasource", "catalog", "dataresource", "datafield" -> CATALOG;
            case "ontology", "entity", "property", "relationship", "action", "rule", "domain", "version" -> ONTOLOGY;
            case "object", "objectinstance", "objectrelationship" -> OBJECT;
            case "workflow", "task", "approval" -> WORKFLOW;
            case "agent", "mission", "tool", "execution" -> AGENT;
            case "knowledge", "glossary", "graph" -> KNOWLEDGE;
            default -> AUDIT;
        };
    }
}
