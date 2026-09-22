package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体映射新增/编辑入参 DTO — T16-2。
 *
 * <p>字段对应 {@code OntologyMappingController.createMapping / updateMapping}
 * 实际消费的业务语义：
 * 新增时 {@code objectId} 必填（兼容旧字段 {@code objectTypeId}）；
 * {@code sourceType} 缺省 "DATASET"（{@code datasetId} 兼容旧字段）。
 *
 * <p>命名与持久化侧的 {@link OntologyMappingSaveDTO} 区分：
 * 本类是 Controller 接收入参（前端字段直读），
 * {@link OntologyMappingSaveDTO} 是 Service 持久化入参（DB 列名映射）。
 */
@Data
public class OntologyMappingCreateDTO {

    /** 本体对象 ID（必填，映射到 entity_code；兼容旧字段 {@code objectTypeId}） */
    private String objectId;

    /** 兼容旧字段 {@code objectTypeId} */
    private String objectTypeId;

    /** 来源名称（写入 entity_name + resource_name） */
    private String sourceName;

    /** 来源类型 / domain code（写入 domain_code；缺省 DATASET，兼容旧 {@code datasetId}） */
    private String sourceType;

    /** 兼容旧字段 {@code datasetId} */
    private String datasetId;

    /** 来源 URI（写入 table_schema） */
    private String sourceUri;

    /** 对象类型（扩展属性，默认 ENTITY） */
    private String objectType;

    /** 字段映射（直接透传到扩展属性 fieldMappings） */
    private java.util.List<java.util.Map<String, Object>> fieldMappings;

    /** 属性映射（扁平 source→target，透传到扩展属性 propertyMappings） */
    private java.util.Map<String, Object> propertyMappings;

    /** 描述（扩展属性 description） */
    private String description;

    /** 状态（扩展属性 status，默认 ACTIVE） */
    private String status;

    /** 是否参与图谱实例化（Q2 裁决新增列，默认 true；false = 显式关闭） */
    private Boolean materialized;

    /** 非结构化文档锚点 JSON 字符串（W2 新增，写入 {@code doc_anchor} 列；可选，{@code null} = 不动/无锚点） */
    private String docAnchorJson;

    /** 锚点类型（W2 新增，写入 {@code doc_anchor_type} 列：TABLE / DOC_ONLY / MIXED） */
    private String docAnchorType;
}
