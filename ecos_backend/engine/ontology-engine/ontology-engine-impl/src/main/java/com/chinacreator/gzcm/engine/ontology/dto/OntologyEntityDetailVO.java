package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体实体（Ontology Entity）详情 VO — {@code GET /ontologies/entities/{id}} 详情接口返回单元。
 *
 * <p>字段对齐 {@code OntologyService.getEntityDetail} 输出（{@code entityToMap} 基础上
 * 嵌套 {@code properties} / {@code relationships} / {@code rules} / {@code actions} 四个子集合）。
 *
 * <p>子集合保留 {@code List<Map<String,Object>>} 是因为子项（property / relationship 等）
 * 也有独立强类型 VO，但本次只改造主实体结构,子 VO 改造放在 T16-2（避免一次 CR 过大）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyEntityDetailVO {

    /** 主键（如 ent501） */
    private String id;

    /** 所属本体 id */
    private String ontologyId;

    /** 实体编码 */
    private String code;

    /** 实体名称 */
    private String name;

    /** 实体描述 */
    private String description;

    /** 实体类型 */
    private String entityType;

    /** 所属域 id */
    private String domainId;

    /** 排序权重 */
    private Integer sortOrder;

    /** 关联映射配置（来自 OntologyMappingStore） */
    private Object mapping;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;

    /** 子属性集合（保留 Map，T16-2 改造为 List<OntologyPropertyVO>） */
    private java.util.List<java.util.Map<String, Object>> properties;

    /** 子关系集合（保留 Map，T16-2 改造为 List<OntologyRelationshipVO>） */
    private java.util.List<java.util.Map<String, Object>> relationships;

    /** 子规则集合（保留 Map，T16-2 改造为 List<OntologyRuleVO>） */
    private java.util.List<java.util.Map<String, Object>> rules;

    /** 子动作集合（保留 Map，T16-2 改造为 List<OntologyActionVO>） */
    private java.util.List<java.util.Map<String, Object>> actions;
}
