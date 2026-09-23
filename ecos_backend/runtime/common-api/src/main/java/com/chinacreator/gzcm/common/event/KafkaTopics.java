package com.chinacreator.gzcm.common.event;

public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String IDENTITY = "ecos.identity";
    public static final String CATALOG = "ecos.catalog";
    /** PMO-data10: 数据资产/字段级敏感度打标事件 — data-engine → security-engine 消费后
     *  生成 ecos_cls_policy（字段级脱敏）+ ecos_rls_policy（L4 clearance 准入），
     *  消费侧 groupId=dccheng-security-consumer。 */
    public static final String DATA_SECURITY_TAGGED = "ecos.data.security-tagged";
    public static final String ONTOLOGY = "ecos.ontology";
    /** PMO-50 T4: 本体版本发布专用 topic — buszhi → dBpe 跨 JVM 事件 (兼容 @EventListener Spring 内存路径)。
     *  与 {@link #ONTOLOGY} 通用主题区分, 消费侧 groupId=dccheng-ontology-consumer, 避免多业务共用。 */
    public static final String ONTOLOGY_PUBLISHED = "ecos.ontology.published";
    public static final String OBJECT = "ecos.object";
    public static final String WORKFLOW = "ecos.workflow";
    public static final String AGENT = "ecos.agent";
    public static final String KNOWLEDGE = "ecos.knowledge";
    /** PMO-59 P2b / ADR-9: 认知心智层事件专用 topic — 假设失效等认知状态变更事件,
     *  消费侧 groupId=dccheng-cognitive-group（{@code CognitiveInvalidationConsumer}）。 */
    public static final String COGNITIVE = "ecos.cognitive";
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
