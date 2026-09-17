package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体属性（Ontology Property）列表项 VO。
 *
 * <p>字段对齐 {@code OntologyService.propToMap} 输出（与 {@code OntologyRepository.PROP_MAPPER}
 * 一致；Map 多出的 enumValues/defaultValue 等扩展字段在 create 写入但 Repository 现读取列未持久化，
 * 实际以 service Map 输出为准）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyPropertyVO {

    /** 主键（如 prop501） */
    private String id;

    /** 所属实体 id */
    private String entityId;

    /** 属性编码（同 entity 内唯一） */
    private String code;

    /** 属性名称 */
    private String name;

    /** 属性描述（本体工作台属性编辑器） */
    private String description;

    /** 属性类型（如 STRING / INTEGER / DECIMAL / DATE） */
    private String propertyType;

    /** 必填标志（1=是 0=否） */
    private Integer requiredFlag;

    /** 可搜索标志（1=是 0=否） */
    private Integer searchableFlag;

    /** 唯一标志（1=是 0=否） */
    private Integer uniqueFlag;

    /** 排序权重 */
    private Integer sortOrder;

    /** 枚举值 JSON 数组（如 ["VIP","Gold","Silver"]） */
    private String enumValues;

    /** 默认值 */
    private String defaultValue;

    /** 校验规则表达式 */
    private String validationRule;

    /** 引用的实体编码（引用类型属性） */
    private String refEntityCode;

    /** 最大长度 */
    private Integer maxLength;

    /** 最小值 */
    private Double minValue;

    /** 最大值 */
    private Double maxValue;

    /** 函数类型（如 EXPRESSION / AGGREGATION / LOOKUP） */
    private String functionType;

    /** 函数表达式文本 */
    private String functionExpression;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;
}
