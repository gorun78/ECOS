package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * F03-01 — 指标口径绑定入参（{@code POST /api/v1/ontology/calibers/metrics/{metricCode}/binding}）。
 */
@Data
public class MetricBindingDTO {
    /** 口径业务编码（ecos_caliber.code）。 */
    private String caliberCode;
    /** 口径版本号（ecos_caliber_version.version_no）。 */
    private String formulaVersion;
}
