package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.List;

import lombok.Data;

/**
 * 词条图谱 VO — 中心词条 N 层邻居的节点与边集合。
 *
 * <p>由 {@code GET /api/v1/ontology/glossary/terms/{id}/graph?depth=N} 返回，
 * 前端直接以此渲染力导向图（无二次补数）。
 */
@Data
public class GlossaryGraphVO {

    /** 中心词条 id */
    private Long centerId;

    /** 展开层数 */
    private Integer depth;

    /** 节点集合（含中心词条） */
    private List<GlossaryGraphNodeVO> nodes;

    /** 边集合（仅连接已纳入节点的边） */
    private List<GlossaryRelationVO> edges;

    /** 节点数 */
    private Integer nodeCount;

    /** 边数 */
    private Integer edgeCount;
}