package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 词条图谱节点 VO — 供前端「图谱」视图渲染（节点 + 边）。
 *
 * <p>仅承载渲染所需最小字段；完整词条详情走 {@code GET /terms} 或词条详情。
 */
@Data
public class GlossaryGraphNodeVO {

    /** 词条主键 */
    private Long id;

    /** 词条编码 */
    private String code;

    /** 词条名称 */
    private String name;

    /** 词条分类 ENTITY/RELATION/METRIC/FUNCTION/CONCEPT */
    private String termType;

    /** 所属领域字典 code */
    private String domain;

    /** 状态 DRAFT/REVIEW/PUBLISHED/DEPRECATED */
    private String status;

    /** 是否为图谱中心词条 */
    private Boolean center;

    /** 距中心词条的跳数（中心为 0） */
    private Integer hop;
}