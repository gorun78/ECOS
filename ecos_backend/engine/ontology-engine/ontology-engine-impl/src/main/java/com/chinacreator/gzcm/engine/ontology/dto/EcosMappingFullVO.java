package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 全局本体映射 VO — GET {@code /api/v1/ecos/mappings/full} 列表响应单元。
 *
 * <p>对应 ecos_entity_table_mapping 表一行（知识工作台"本体模型"Tab 一次性渲染需要）。
 * 字段命名沿用 {@code OntologyMappingController} 旧契约：
 * <ul>
 *   <li>{@code entityCode}   ← entity_code (本体对象 id)</li>
 *   <li>{@code sourceDomain} ← domain_code (来源类型语义, 前端按 sourceType 消费)</li>
 *   <li>{@code version}      ← version 表主键 (兼容前端字段)；本表无 version 列时返回 ""</li>
 *   <li>{@code fieldMappings} ← field_mappings JSONB (反序列化为 List)</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EcosMappingFullVO {

    private String id;
    private String entityCode;
    private String sourceDomain;
    private String version;
    private List<Map<String, Object>> fieldMappings;

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getEntityCode() { return entityCode; }

    public void setEntityCode(String entityCode) { this.entityCode = entityCode; }

    public String getSourceDomain() { return sourceDomain; }

    public void setSourceDomain(String sourceDomain) { this.sourceDomain = sourceDomain; }

    public String getVersion() { return version; }

    public void setVersion(String version) { this.version = version; }

    public List<Map<String, Object>> getFieldMappings() { return fieldMappings; }

    public void setFieldMappings(List<Map<String, Object>> fieldMappings) { this.fieldMappings = fieldMappings; }
}
