package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 词条关系边 VO。
 *
 * <p>在持久化字段基础上补入两端词条名称（{@code fromTermName / toTermName}），
 * 使前端关系列表与图谱无需二次查名（避免 N+1）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GlossaryRelationVO {

    /** 主键 */
    private Long id;

    /** 关系起点词条 id */
    private Long fromTermId;

    /** 关系起点词条名称 */
    private String fromTermName;

    /** 关系终点词条 id */
    private Long toTermId;

    /** 关系终点词条名称 */
    private String toTermName;

    /** 边类型 ISA/SYNONYM/PART_OF/SEE_ALSO/CAUSAL/RELATED */
    private String relationType;

    /** 关系权重 */
    private Integer weight;

    /** 关系说明 */
    private String description;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 ISO 字符串 */
    private String createdAt;
}