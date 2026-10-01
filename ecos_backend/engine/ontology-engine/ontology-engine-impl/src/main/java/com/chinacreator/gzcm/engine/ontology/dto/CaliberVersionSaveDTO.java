package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * F03-01 — 口径升版入参（{@code POST /api/v1/ontology/calibers/{code}/versions}）。
 */
@Data
public class CaliberVersionSaveDTO {
    /** 版本号；缺省由 Controller 计入 {@code version_no} 序列。 */
    private String versionNo;
    /** 表达式明文（公式 DSL）。 */
    private String formula;
    /** 可加性数值码：1 ADDITIVE / 2 SEMI / 3 NON。 */
    private Integer additive;
    /** 周期粒度：MONTH / QUARTER / YEAR。 */
    private String periodGranularity;
    /** 状态；缺省 DRAFT。 */
    private String status;
}
