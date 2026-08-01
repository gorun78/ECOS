package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 混合检索推理的子问题定义。
 * 由 Planner 根据问题类型动态构建，Reasoner 根据类型选择执行策略。
 */
public class SubQuery {

    /** 子问题类型 */
    private SubQueryType type;

    /** KG_QUERY: Cypher 查询语句 */
    private String cypher;

    /** RULE_CHECK / HYBRID: 适用对象类型（如"医疗器械"） */
    private String objectType;

    /** RULE_CHECK / HYBRID: 业务事实上下文（key-value pairs） */
    private Map<String, Object> facts;

    /** VECTOR_RAG / HYBRID: 语义检索查询文本 */
    private String semanticQuery;

    public SubQuery() {
        this.facts = new LinkedHashMap<>();
    }

    public SubQueryType getType() { return type; }
    public void setType(SubQueryType type) { this.type = type; }
    public String getCypher() { return cypher; }
    public void setCypher(String cypher) { this.cypher = cypher; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public Map<String, Object> getFacts() { return facts; }
    public void setFacts(Map<String, Object> facts) { this.facts = facts; }
    public String getSemanticQuery() { return semanticQuery; }
    public void setSemanticQuery(String semanticQuery) { this.semanticQuery = semanticQuery; }

    /** 子问题类型枚举 */
    public enum SubQueryType {
        /** 知识图谱查询 — 通过 Cypher 直接查 Neo4j */
        KG_QUERY,
        /** 规则检查 — 匹配合规规则并逐条评估条件 */
        RULE_CHECK,
        /** 向量 RAG 检索 — 语义检索 + LLM 生成 */
        VECTOR_RAG,
        /** 混合检索 — KG + 规则 + RAG 融合 */
        HYBRID
    }
}
