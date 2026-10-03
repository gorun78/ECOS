package com.chinacreator.gzcm.engine.ontology.model;

import java.time.LocalDateTime;

/**
 * 指标定义模型（表 {@code ecos_ontology.ecos_metric_definition}，V168.1 合规新表，F03-09 语义二分"定义"侧唯一可写入口）。
 */
public class MetricDefinition {
    private String id;
    private String code;
    private String name;
    private String expression;
    private String aggregation;
    private String entityCode;
    private String caliberId;         // DRAFT 可空；PUBLISHED 必填
    private String formulaVersion;
    private String caliberSnapshot;   // 发布时刻一次性写入，之后只读
    private String status;            // DRAFT → PUBLISHED → SUPERSEDED
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
    private Integer isDeleted;
    private String versionNo;
    private String domain;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public String getCaliberSnapshot() { return caliberSnapshot; }
    public void setCaliberSnapshot(String caliberSnapshot) { this.caliberSnapshot = caliberSnapshot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
}
