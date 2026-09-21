package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 词条关系边新增 SaveDTO。
 *
 * <p>关系为有向边，更新语义采用「删旧边 + 建新边」而非原地改（保持边集合清晰），
 * 故仅提供新增入参。
 */
@Data
public class GlossaryRelationSaveDTO {

    /** 关系起点词条 id */
    private Long fromTermId;

    /** 关系终点词条 id */
    private Long toTermId;

    /** 边类型 ISA/SYNONYM/PART_OF/SEE_ALSO/CAUSAL/RELATED */
    private String relationType;

    /** 关系权重（可空，默认 100） */
    private Integer weight;

    /** 关系说明 */
    private String description;

    /** 创建人 */
    private String createdBy;
}