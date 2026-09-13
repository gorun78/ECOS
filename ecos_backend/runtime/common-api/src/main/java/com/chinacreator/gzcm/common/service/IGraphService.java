package com.chinacreator.gzcm.common.service;

import java.util.List;
import java.util.Map;

/**
 * A1: 图服务抽象 — 统一 Neo4j 和 PostgreSQL 图查询接口。
 *
 * <p>企业版/旗舰版使用 Neo4jGraphService，
 * 标准版使用 PgGraphService 回退到 PG 关系表。</p>
 */
public interface IGraphService {

    /**
     * 执行 Cypher / 类 Cypher 查询，返回记录列表。
     *
     * @param cypherPattern Cypher 查询模式（Neo4j 直通；PG 版做简单翻译）
     * @param params        查询参数
     * @return 记录列表，每条记录是一个 Map
     */
    List<Map<String, Object>> query(String cypherPattern, Map<String, Object> params);

    /**
     * 创建图节点。
     *
     * @param label 节点标签
     * @param props 节点属性
     */
    void createNode(String label, Map<String, Object> props);

    /**
     * 创建两节点间的关系。
     *
     * @param fromId  源节点 ID
     * @param toId    目标节点 ID
     * @param relType 关系类型
     */
    void createRelationship(String fromId, String toId, String relType);

    /**
     * 获取以 entityId 为中心的子图（节点 + 边）。
     *
     * @param entityId 实体 ID
     * @return 包含 nodes 和 edges 的 Map
     */
    Map<String, Object> getSubgraph(String entityId);
}
