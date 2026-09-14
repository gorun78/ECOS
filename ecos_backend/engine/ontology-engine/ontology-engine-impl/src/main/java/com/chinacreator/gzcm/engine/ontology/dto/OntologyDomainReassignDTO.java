package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 对象归属域变更 DTO — T16-2 强类型入参。
 *
 * <p>用于 {@code PUT /api/v1/ontology/objects/{id}/domain}：
 * 字段 {@code domainCode} 可为领域 code 或 id；兼容旧字段 {@code domainId}。
 */
@Data
public class OntologyDomainReassignDTO {

    /** 目标领域 code（首选） */
    private String domainCode;

    /** 目标领域 id（兼容旧字段；当 domainCode 为空时取用） */
    private String domainId;
}
