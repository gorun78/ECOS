package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体映射持久化字段 — T16-2。
 *
 * <p>字段对齐 {@code OntologyMappingService.insertMapping / insertMappingVO} 实际写入
 * {@code ecos_entity_table_mapping} 表的列（除主键 id 外）。
 *
 * <p>{@code fieldMappingsJson} 为 MAPPER 序列化后的 JSON 字符串（{@code ::jsonb}），
 * 由 Controller 在 Service 调用前序列化完成。
 */
@Data
public class OntologyMappingSaveDTO {

    /** 本体对象 code（映射到 entity_code） */
    private String objectId;

    /** 来源名称（写入 entity_name + resource_name） */
    private String sourceName;

    /** 来源类型 / domain code（写入 domain_code） */
    private String sourceType;

    /** 来源 URI（写入 table_schema） */
    private String sourceUri;

    /** 扩展属性（JSON 字符串，{@code field_mappings} 列 jsonb） */
    private String fieldMappingsJson;

    /** 是否参与图谱实例化（Q2 裁决新增列；{@code null} 时按默认 {@code true} 落库） */
    private Boolean materialized;
}
