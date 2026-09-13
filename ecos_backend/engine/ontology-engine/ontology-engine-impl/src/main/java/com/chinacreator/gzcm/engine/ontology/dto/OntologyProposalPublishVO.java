package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

import java.util.Map;

/**
 * 本体变更提案 approve-and-publish 结果 VO。
 *
 * <p>{@code POST /api/v1/ontology/proposals/{id}/approve-and-publish} 返回。
 *
 * <p>{@code proposal} 为最终提案记录（PG 行 Map），动态嵌套数据豁免。
 */
@Data
public class OntologyProposalPublishVO {

    /** 终态（EXECUTED） */
    private String status;

    /** 关联版本 id（字符串） */
    private String versionId;

    /** 最终提案记录（PG 行 Map） */
    private Map<String, Object> proposal;
}
