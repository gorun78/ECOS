package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 本体映射（Ontology Mapping）VO — T16-2 强类型返回。
 *
 * <p>字段对齐 {@code OntologyMappingController.rowToApiMap} 输出：
 * id / objectId / objectTypeId / datasetId / objectType / sourceType /
 * sourceName / sourceUri / fieldMappings / propertyMappings / description /
 * status / createdAt / updatedAt。
 *
 * <p>{@code fieldMappings} 是兼容旧契约的数组（元素为 {@code {source, target}} 对），
 * 保持 {@code List<Map<String,Object>>}（动态嵌套数据，与既有 Map 形态一致）。
 * {@code propertyMappings} 是 source→target 的扁平 Map。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyMappingVO {

    /** 映射主键 ID（UUID 去连字符） */
    private String id;

    /** 本体对象 ID（= entity_code） */
    private String objectId;

    /** 兼容旧字段：objectTypeId（= entity_code） */
    private String objectTypeId;

    /** 兼容旧字段：datasetId（= 扩展属性 datasetId，缺省 domain_code） */
    private Object datasetId;

    /** 对象类型（默认 ENTITY） */
    private String objectType;

    /** 来源类型（= domain_code） */
    private String sourceType;

    /** 本体实体编码（= entity_code，与 objectId 同源；B3-2 实例抽取入口显式字段） */
    private String entityCode;

    /** 来源名称（= entity_name，缺省 resource_name） */
    private String sourceName;

    /** 目标 DW 表名（= resource_name；B3-2 实例抽取入口显式字段） */
    private String resourceName;

    /** 来源 URI（= table_schema） */
    private String sourceUri;

    /** 字段映射列表（兼容旧契约；元素为 {source, target}） */
    private List<Map<String, Object>> fieldMappings;

    /** 属性映射（source → target 扁平 Map） */
    private Map<String, Object> propertyMappings;

    /** 描述（来自扩展属性 description） */
    private String description;

    /** 状态（默认 ACTIVE） */
    private String status;

    /** 是否参与实例化（Q2 裁决新增列，默认 true；false = 显式关闭图谱实例化） */
    private Boolean materialized;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;
}
