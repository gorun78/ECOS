package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 对象归属域变更结果 VO — T16-2 强类型返回。
 *
 * <p>字段对齐 {@code OntologyDomainService.reassignEntityDomain} 输出：
 * entityId / domainId / domainCode / domainName（四字段）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyDomainReassignVO {

    /** 被重分配的实体（对象）ID */
    private String entityId;

    /** 目标领域 ID */
    private String domainId;

    /** 目标领域 code */
    private String domainCode;

    /** 目标领域名称 */
    private String domainName;
}
