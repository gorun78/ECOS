package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体属性（Ontology Property）新增/编辑 DTO。
 *
 * <p>字段对应 {@code OntologyService.createProperty / updateProperty} 实际消费的业务语义。
 * 不更新字段使用 null（service 内 hasKey 语义判断是否送更新），不传字段的语义差异与
 * 老 Map 路径 {@code containsKey=false} 等价。
 */
@Data
public class OntologyPropertySaveDTO {

    /** 属性编码（新增必填；同 entity 内唯一） */
    private String code;

    /** 属性名称（新增必填） */
    private String name;

    /** 属性描述（null=不更新；空串=清空） */
    private String description;

    /** 属性类型（默认 STRING） */
    private String propertyType;

    /** 必填标志（null=不更新，新增默认 0） */
    private Integer requiredFlag;

    /** 可搜索标志（null=不更新，新增默认 0） */
    private Integer searchableFlag;

    /** 唯一标志（null=不更新，新增默认 0） */
    private Integer uniqueFlag;

    /** 枚举值 JSON 数组（null=不更新） */
    private String enumValues;

    /** 默认值（null=不更新） */
    private String defaultValue;

    /** 校验规则表达式（null=不更新） */
    private String validationRule;

    /** 引用的实体编码（null=不更新） */
    private String refEntityCode;

    /** 最大长度（null=不更新） */
    private Integer maxLength;

    /** 最小值（null=不更新） */
    private Double minValue;

    /** 最大值（null=不更新） */
    private Double maxValue;

    /** 函数类型（null=不更新） */
    private String functionType;

    /** 函数表达式文本（null=不更新） */
    private String functionExpression;
}
