package com.chinacreator.gzcm.engine.ontology.dto;

/** 口径版本入参（POST /api/v1/ontology/calibers/{code}/versions）。一次升版一行，行不可变。 */
public class CaliberVersionSaveDTO {
    private String versionNo;
    private String formula;
    private Integer additive;   // 1 ADDITIVE / 2 SEMI / 3 NON
    private String periodGranularity; // MONTH / QUARTER / YEAR
    private String status;       // DRAFT / REVIEWING / APPROVED / RETIRED
    private String approvedBy;

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getFormula() { return formula; }
    public void setFormula(String formula) { this.formula = formula; }
    public Integer getAdditive() { return additive; }
    public void setAdditive(Integer additive) { this.additive = additive; }
    public String getPeriodGranularity() { return periodGranularity; }
    public void setPeriodGranularity(String periodGranularity) { this.periodGranularity = periodGranularity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
}
