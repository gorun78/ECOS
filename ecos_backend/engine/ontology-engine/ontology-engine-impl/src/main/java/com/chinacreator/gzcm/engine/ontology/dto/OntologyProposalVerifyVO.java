package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 本体变更提案验证结果 VO。
 *
 * <p>{@code POST /api/v1/ontology/proposals/{id}/verify} 返回。
 *
 * <p>{@code proposal} 字段为完整提案记录（PG 行 Map），属动态嵌套数据豁免
 * （PG JDBC 直出，与 {@code OntologyProposalVO} 字段同构但不重复 VO 化）。
 */
@Data
public class OntologyProposalVerifyVO {

    /** 是否通过验证（missing displayName/name、payload 可解析等） */
    private Boolean valid;

    /** 验证问题列表 */
    private List<String> issues;

    /** 验证后的提案记录（PG 行 Map，JSON 序列化时 PGobject 由 ObjectMapper 默认处理） */
    private Map<String, Object> proposal;
}
