package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 关系校验请求 Query DTO — {@code POST /relationships/validate} 入参。
 *
 * <p>字段对齐 {@code OntologyRelationshipController.validateRelationship} 实际消费的两项。
 */
@Data
public class OntologyRelationshipValidateQuery {

    /** 源实体 id（必填） */
    private String sourceEntityId;

    /** 目标实体 id（必填） */
    private String targetEntityId;
}
