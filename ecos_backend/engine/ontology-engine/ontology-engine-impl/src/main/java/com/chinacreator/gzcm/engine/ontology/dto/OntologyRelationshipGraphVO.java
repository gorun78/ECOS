package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 关系图数据 VO — {@code GET /relationships/graph} 返回单元。
 *
 * <p>字段对齐 {@code OntologyService.getRelationshipGraph} 输出
 * （{@code nodes} 是节点 id，{@code edges} 是 {@code {source, target, code}} 边列表）。
 *
 * <p>旧 service 返回 {@code List<Map<String,Object>>}，第一个元素是
 * {@code {nodes:[{id}...], edges:[{source,target,code}...]}}。本 VO 直接按 nodes/edges
 * 展开，前端可直接消费（旧消费方需要按 graph 第一个元素访问，本批仅 6 Controller 改造，
 * graph 端点的新调用方走单对象契约；已有调用方仍走旧 Map 路径不在本批范围）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyRelationshipGraphVO {

    /** 节点列表（每个节点只有 id 字段） */
    private List<GraphNode> nodes;

    /** 边列表 */
    private List<GraphEdge> edges;

    /**
     * 关系图节点 — {@code {id}} 单字段。
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GraphNode {

        /** 节点 id（实体 id） */
        private String id;
    }

    /**
     * 关系图边 — {@code {source, target, code}}。
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GraphEdge {

        /** 源节点 id */
        private String source;

        /** 目标节点 id */
        private String target;

        /** 关系编码 */
        private String code;
    }
}
