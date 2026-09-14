package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体工作台视图对象（object 视图）列表项 VO — Wave B-5 T7。
 *
 * <p>对应 {@code GET /api/v1/ecos/ontologies/{id}/objects?type=X} 返回项。
 * 命名带 Workbench 前缀：与 T16-2 既有 {@code OntologyObjectSaveDTO}
 * （"对象类型/entity"语义）区分，本 VO 为工作台"object 视图"对象。
 * {@code definition} 为 definitionJson 解析后的结构化值（Object），
 * 与 T16-1 {@code OntologyActionVO.preconditions} 同豁免策略。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyWorkbenchObjectVO {

    /** 主键（fileId） */
    private String id;

    /** 所属本体 id */
    private String ontologyId;

    /** 视图类型: action / interface / shared_property / function / dataset */
    private String objectType;

    /** 对象编码 */
    private String code;

    /** 对象名称 */
    private String name;

    /** 对象描述 */
    private String description;

    /** 视图类型差异字段（JSON 解析后对象） */
    private Object definition;

    /** 状态（ACTIVE / ARCHIVED） */
    private String status;

    /** 创建时间 ISO 字符串 */
    private String createTime;

    /** 更新时间 ISO 字符串 */
    private String updateTime;
}
