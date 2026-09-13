package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体领域（Ontology Domain）VO — T16-2 强类型返回。
 *
 * <p>字段对齐 {@code OntologyDomainService.toMap(OntologyDomain)} 输出
 * （id/code/name/owner/description/status/sortOrder/createdAt/updatedAt）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyDomainVO {

    /** 主键（dom 前缀） */
    private String id;

    /** 领域编码（唯一） */
    private String code;

    /** 领域名称 */
    private String name;

    /** 所属 owner */
    private String owner;

    /** 领域描述 */
    private String description;

    /** 状态（Draft / Published / Deprecated） */
    private String status;

    /** 排序权重 */
    private Integer sortOrder;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;
}
