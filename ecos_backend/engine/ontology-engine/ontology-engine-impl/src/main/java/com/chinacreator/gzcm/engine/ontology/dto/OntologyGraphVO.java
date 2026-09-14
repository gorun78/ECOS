package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体知识图谱返回 VO — T16-4 强类型返回容器。
 *
 * <p>对齐 {@code IGraphService.getSubgraph / query} 既有 Map 输出契约：
 * {@code nodes} / {@code edges} 为动态图数据（Neo4j 节点/边属性随本体演进），
 * 保持 Object 类型以兼容动态结构（T16-4: Graph 节点/边动态结构豁免）。
 *
 * <p>{@code traceNode} 端点额外返回 {@code tracePath}（动态路径列表）。
 * 异常分支的 {@code message} 字段用于前端提示（NON_NULL，正常时不输出）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyGraphVO {

    /**
     * 图节点列表 — 动态结构（Neo4j 节点 Map 集合 / PG 回退），保持 Object 豁免。
     */
    private Object nodes;

    /**
     * 图边列表 — 动态结构（Neo4j 关系 Map 集合 / PG 回退），保持 Object 豁免。
     */
    private Object edges;

    /**
     * 节点追溯路径（仅 traceNode 端点填充；动态路径列表）。
     */
    private Object tracePath;

    /**
     * 错误提示消息（服务不可用时填充；正常时 null 不输出）。
     */
    private String message;
}
