package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 本体实体依赖分析 VO — {@code /ontologies/entities/{entityId}/dependencies} 详情接口返回单元。
 *
 * <p>字段对齐 {@code OntologyService.getEntityDependencies} 输出。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyEntityDependenciesVO {

    /** 主实体 id */
    private String entityId;

    /** 直接关联的实体 id 列表（通过 relationship 关联） */
    private List<String> relatedEntities;

    /** 子属性数量 */
    private int propertyCount;

    /** 子关系数量 */
    private int relationshipCount;

    /** 级联删除影响范围（固定 ["properties","relationships","actions"]） */
    private List<String> cascadingDeletes;
}
