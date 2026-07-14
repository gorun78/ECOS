package com.chinacreator.gzcm.worldmodel;

import java.time.LocalDateTime;

/**
 * World Model 目标持久化实体
 * <p>
 * 对应 ecos_wm_goal 表，存储目标节点信息及层级关系。
 * </p>
 */
public class GoalEntity {

    private Long id;
    private String name;
    private String description;
    private Long parentId;
    private Integer progress;
    private String status;
    private String goalType;
    private Integer weight;
    private String orgId;
    private String ownerUserId;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private java.math.BigDecimal targetValue;
    private java.math.BigDecimal currentValue;
    private String unit;
    private String linkedWorkflowId;
    private String kpiFormula;
    private String measureFrequency;
    private java.math.BigDecimal alertThresholdWarn;
    private java.math.BigDecimal alertThresholdCritical;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public GoalEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

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

    public java.time.LocalDate getStartDate() { return startDate; }
    public void setStartDate(java.time.LocalDate startDate) { this.startDate = startDate; }

    public java.time.LocalDate getEndDate() { return endDate; }
    public void setEndDate(java.time.LocalDate endDate) { this.endDate = endDate; }

    public java.math.BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(java.math.BigDecimal targetValue) { this.targetValue = targetValue; }

    public java.math.BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(java.math.BigDecimal currentValue) { this.currentValue = currentValue; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getLinkedWorkflowId() { return linkedWorkflowId; }
    public void setLinkedWorkflowId(String linkedWorkflowId) { this.linkedWorkflowId = linkedWorkflowId; }

    public String getKpiFormula() { return kpiFormula; }
    public void setKpiFormula(String kpiFormula) { this.kpiFormula = kpiFormula; }

    public String getMeasureFrequency() { return measureFrequency; }
    public void setMeasureFrequency(String measureFrequency) { this.measureFrequency = measureFrequency; }

    public java.math.BigDecimal getAlertThresholdWarn() { return alertThresholdWarn; }
    public void setAlertThresholdWarn(java.math.BigDecimal alertThresholdWarn) { this.alertThresholdWarn = alertThresholdWarn; }

    public java.math.BigDecimal getAlertThresholdCritical() { return alertThresholdCritical; }
    public void setAlertThresholdCritical(java.math.BigDecimal alertThresholdCritical) { this.alertThresholdCritical = alertThresholdCritical; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
