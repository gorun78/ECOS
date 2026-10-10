package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.time.LocalDate;

/**
 * V227 {@code ecos_ai.ecos_wagent_goal} 业务目标 DO（对象链根，REQ-WAG-05/06）。
 * <p>{@code goal_type} 8 意图单源 = {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent}；
 * {@code slots_source_json} 逐槽位来源标注（SlotProvenance 四值）；JSON 语义列一律 TEXT（DR04/MC02）。</p>
 */
public class WAgentGoalDO extends WAgentBaseDO {

    private String id;
    private String name;
    private String goalType;
    private String scopeJson;
    private String timeRangeText;
    private LocalDate asOfDate;
    private String metricId;
    private String metricVersion;
    private String caliberVersion;
    private String successCriteriaText;
    private String ownerId;
    private LocalDate dueDate;
    private String status;
    private String slotsSourceJson;
    private Integer inferredCount;
    private String supersedesGoalId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public String getScopeJson() { return scopeJson; }
    public void setScopeJson(String scopeJson) { this.scopeJson = scopeJson; }

    public String getTimeRangeText() { return timeRangeText; }
    public void setTimeRangeText(String timeRangeText) { this.timeRangeText = timeRangeText; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public String getMetricId() { return metricId; }
    public void setMetricId(String metricId) { this.metricId = metricId; }

    public String getMetricVersion() { return metricVersion; }
    public void setMetricVersion(String metricVersion) { this.metricVersion = metricVersion; }

    public String getCaliberVersion() { return caliberVersion; }
    public void setCaliberVersion(String caliberVersion) { this.caliberVersion = caliberVersion; }

    public String getSuccessCriteriaText() { return successCriteriaText; }
    public void setSuccessCriteriaText(String successCriteriaText) { this.successCriteriaText = successCriteriaText; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSlotsSourceJson() { return slotsSourceJson; }
    public void setSlotsSourceJson(String slotsSourceJson) { this.slotsSourceJson = slotsSourceJson; }

    public Integer getInferredCount() { return inferredCount; }
    public void setInferredCount(Integer inferredCount) { this.inferredCount = inferredCount; }

    public String getSupersedesGoalId() { return supersedesGoalId; }
    public void setSupersedesGoalId(String supersedesGoalId) { this.supersedesGoalId = supersedesGoalId; }
}
