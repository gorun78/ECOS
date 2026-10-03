package com.chinacreator.gzcm.engine.ontology.model;

import java.time.LocalDateTime;

/**
 * 口径版本模型（表 {@code ecos_ontology.ecos_caliber_version}，V167.1）。
 * 行不可变：一次升版一行，status ∈ DRAFT/REVIEWING/APPROVED/RETIRED，历史内容以 Git 归档为准（git_ref）。
 */
public class CaliberVersion {
    private String id;
    private String caliberId;
    private String versionNo;
    private String formula;
    private Integer additive;      // 1 ADDITIVE / 2 SEMI / 3 NON
    private String periodGranularity; // MONTH / QUARTER / YEAR
    private String status;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String gitRef;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
    private Integer isDeleted;
    private String domain;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCaliberId() { return caliberId; }
    public void setCaliberId(String caliberId) { this.caliberId = caliberId; }
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
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getGitRef() { return gitRef; }
    public void setGitRef(String gitRef) { this.gitRef = gitRef; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
}
