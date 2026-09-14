package com.chinacreator.gzcm.sysman.controller.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ECOS World Model — 目标模型 (Phase 2: 支持 PostgreSQL 持久化字段)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoalEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String description;
    private String parentId;
    private Integer progress;
    private String status;
    private String goalType;
    private Integer weight;
    private String orgId;
    private String ownerUserId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal targetValue;
    private BigDecimal currentValue;
    private String unit;
    private String linkedWorkflowId;
    private String kpiFormula;
    private String measureFrequency;
    private BigDecimal alertThresholdWarn;
    private BigDecimal alertThresholdCritical;
    private Long createdAt;
    private Long updatedAt;

    public GoalEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getLinkedWorkflowId() { return linkedWorkflowId; }
    public void setLinkedWorkflowId(String linkedWorkflowId) { this.linkedWorkflowId = linkedWorkflowId; }
    public String getKpiFormula() { return kpiFormula; }
    public void setKpiFormula(String kpiFormula) { this.kpiFormula = kpiFormula; }
    public String getMeasureFrequency() { return measureFrequency; }
    public void setMeasureFrequency(String measureFrequency) { this.measureFrequency = measureFrequency; }
    public BigDecimal getAlertThresholdWarn() { return alertThresholdWarn; }
    public void setAlertThresholdWarn(BigDecimal alertThresholdWarn) { this.alertThresholdWarn = alertThresholdWarn; }
    public BigDecimal getAlertThresholdCritical() { return alertThresholdCritical; }
    public void setAlertThresholdCritical(BigDecimal alertThresholdCritical) { this.alertThresholdCritical = alertThresholdCritical; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
