package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 抽取候选条目 VO — {@code GET /api/v1/knowledge/extract/candidates/{fileId}} 出参元素。
 *
 * <p>字段名按前端 {@code ExtractionReviewTab} 的消费键对齐：
 * {@code type ?? entityType ?? kind ?? candidateId}、{@code description ?? label ?? value}、
 * {@code confidence}、{@code rejected}。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtractCandidateVO {

    /** 候选 ID（实体内候选，形如 entity-0 / link-0 / rule-0） */
    private String candidateId;

    /** 候选类别：entity / link / rule */
    private String kind;

    /** 候选类型（实体类型 / 关系类型 / rule 固定 RULE） */
    private String type;

    /** 候选描述（实体名 / 关系 from→to / 规则名+描述） */
    private String description;

    /** 置信度（规则类候选无该值，为 null） */
    private Double confidence;

    /** 是否已被驳回（当前数据源无逐条驳回标记，恒为 false） */
    private boolean rejected;
}
