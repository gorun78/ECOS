package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体工作台视图对象（object 视图）新增/编辑 DTO — Wave B-5 T7。
 *
 * <p>对应 {@code POST/PUT /api/v1/ecos/ontologies/{id}/objects/{fileId}} 入参。
 *
 * <p>命名带 Workbench 前缀：与 T16-2 既有 {@code OntologyObjectSaveDTO}
 * （"对象类型/entity"语义）区分，本 DTO 为工作台"object 视图"对象。
 *
 * <p>{@code definition} 承载 5 类视图差异字段（前端视图对象结构
 * action/interface/shared_property/function/dataset），类型保留 {@code Object}
 * 与 T16-1 {@code OntologyActionSaveDTO.preconditions} 同豁免策略：
 * service 用 Jackson 写为 JSON String 落库，前端可传 Map/List/String。
 * 只豁免嵌套值，外层强类型不变。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyWorkbenchObjectSaveDTO {

    /** 视图类型（新增必填）: action / interface / shared_property / function / dataset */
    private String objectType;

    /** 对象编码（必填；同 ontology + objectType 内唯一；null=不更新） */
    private String code;

    /** 对象名称（必填；null=不更新） */
    private String name;

    /** 对象描述（null=不更新） */
    private String description;

    /** 视图类型差异字段（前端视图对象结构；null=不更新） */
    private Object definition;

    /** 状态（新增默认 ACTIVE；null=不更新） */
    private String status;
}
