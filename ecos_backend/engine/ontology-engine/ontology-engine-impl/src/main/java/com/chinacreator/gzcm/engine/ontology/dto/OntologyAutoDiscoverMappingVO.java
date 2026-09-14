package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 实体表映射查询 VO。
 *
 * <p>字段对齐 {@code ecos_entity_table_mapping} 表列（与
 * {@code AutoDiscoverService.listMappings} 原 {@code SELECT *} 投影
 * 一致）：id / entity_code / entity_name / domain_code /
 * datasource_id / resource_name / table_schema / field_mappings /
 * created_at / updated_at。
 *
 * <p>{@code fieldMappings} 为 JSONB 列（PG JDBC 返回 PGobject），
 * POJO 用 {@code Object} 承载（动态嵌套数据豁免，与
 * {@code OntologyEntityVO.mapping} 同款）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyAutoDiscoverMappingVO {

    /** 主键 id */
    private Object id;

    /** 实体编码 */
    private String entityCode;

    /** 实体名称 */
    private String entityName;

    /** 所属域 code */
    private String domainCode;

    /** 数据源 id */
    private String datasourceId;

    /** 原资源名（表名） */
    private String resourceName;

    /** 表 schema（可能为空） */
    private String tableSchema;

    /** 字段映射 JSONB（PGobject → 反序列化后对象） */
    private Object fieldMappings;

    /** 创建时间（ISO 字符串） */
    private String createdAt;

    /** 更新时间（ISO 字符串） */
    private String updatedAt;
}
