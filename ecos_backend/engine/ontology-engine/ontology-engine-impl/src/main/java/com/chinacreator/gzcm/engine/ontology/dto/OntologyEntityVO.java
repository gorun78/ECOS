package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体实体（Ontology Entity）列表项 VO — {@code /ontologies/{id}/entities} 等接口返回单元。
 *
 * <p>字段对齐 {@code OntologyService.entityToMap} 输出（与 {@code OntologyRepository.ENTITY_MAPPER}
 * 一致 + {@code OntologyMappingStore} 的关联映射）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyEntityVO {

    /** 主键（如 ent501） */
    private String id;

    /** 所属本体 id */
    private String ontologyId;

    /** 实体编码（同 ontology 内唯一） */
    private String code;

    /** 实体名称 */
    private String name;

    /** 实体描述 */
    private String description;

    /** 实体类型（如 MASTER / CHILD / REFERENCE） */
    private String entityType;

    /** 所属域 id */
    private String domainId;

    /** 排序权重（建表默认 1） */
    private Integer sortOrder;

    /**
     * 关联映射配置，来自 {@code OntologyMappingStore.store}（key = entity id）。
     *
     * <p>值类型保持 {@code Object} 是为了兼容历史 Map 行为（前端可能传入对象或字符串）；
     * 不属于"接口强类型 VO"约束违规，而是 Map 内容嵌套数据（Java 端无法静态确定业务结构）。
     */
    private Object mapping;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;
}
