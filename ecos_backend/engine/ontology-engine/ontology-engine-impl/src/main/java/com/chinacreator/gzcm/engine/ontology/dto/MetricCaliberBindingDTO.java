package com.chinacreator.gzcm.engine.ontology.dto;

/** 指标显式绑定口径版本入参（POST /api/v1/ontology/metrics/{code}/binding）。 */
public class MetricCaliberBindingDTO {
    private String caliberId;
    private String formulaVersion;   // 目标口径版本 version_no
    private String status;           // 目标指标状态（DRAFT/PUBLISHED/SUPERSEDED）

    public String getCaliberId() { return caliberId; }
    public void setCaliberId(String caliberId) { this.caliberId = caliberId; }
    public String getFormulaVersion() { return formulaVersion; }
    public void setFormulaVersion(String formulaVersion) { this.formulaVersion = formulaVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
