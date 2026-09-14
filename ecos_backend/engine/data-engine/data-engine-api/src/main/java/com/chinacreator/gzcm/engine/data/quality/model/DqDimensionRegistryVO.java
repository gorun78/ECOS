package com.chinacreator.gzcm.engine.data.quality.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 维度注册表 VO — 6 维 rule_type 映射（dimension-registry 端点出参）。
 * <p>每个维度含: 维度编码 / 中文名 / 对应 rule_type 列表 / 综合评分默认权重 / 描述。</p>
 *
 * @author PMO-48-A T3
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqDimensionRegistryVO {

    /** 维度编码: COMPLETENESS / ACCURACY / CONSISTENCY / FRESHNESS / UNIQUENESS / VALIDITY */
    private String dimension;

    /** 维度中文名 */
    private String name;

    /** 该维度映射的 rule_type 列表 */
    private List<String> ruleTypes;

    /** 综合评分默认权重 (0-100) */
    private Integer defaultWeight;

    /** 维度描述 */
    private String description;
}
