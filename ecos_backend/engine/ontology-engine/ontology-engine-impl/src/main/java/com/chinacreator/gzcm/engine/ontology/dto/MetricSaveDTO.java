package com.chinacreator.gzcm.engine.ontology.dto;

/** 指标保存入参（本地登记 biz-profit 10 项指标时经此创建/更新）。 */
public class MetricSaveDTO {
    private String code;
    private String name;
    private String expression;
    private String aggregation;
    private String entityCode;
    private String caliberId;
    private String formulaVersion;
    private String status;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getAggregation() { return aggregation; }
    public void setAggregation(String aggregation) { this.aggregation = aggregation; }
    public String getEntityCode() { return entityCode; }
    public void setEntityCode(String entityCode) { this.entityCode = entityCode; }
    public String getCaliberId() { return caliberId; }
    public void setCaliberId(String caliberId) { this.caliberId = caliberId; }
    public String getFormulaVersion() { return formulaVersion; }
    public void setFormulaVersion(String formulaVersion) { this.formulaVersion = formulaVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
