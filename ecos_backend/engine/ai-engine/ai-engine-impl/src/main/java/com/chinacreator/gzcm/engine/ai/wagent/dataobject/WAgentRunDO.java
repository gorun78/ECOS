package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.time.LocalDateTime;

/**
 * V233 {@code ecos_ai.ecos_wagent_run} 运行实例 DO（Run 状态机/预算/幂等，REQ-WAG-11）。
 * <p>{@code status} ∈ WAgentEnums.RunStatus 13 态；{@code plan_source} ∈ {PLAYBOOK,EXPLORED}；
 * 幂等 UNIQUE(tenant_id, initiated_by, idempotency_key, is_deleted)（应用侧先查后插 + DB 唯一索引）；
 * {@code situation_snapshot_json} = Situation 11 项受控快照唯一落点（§9.2，禁在 ecos_cognitive_* 建表）。</p>
 */
public class WAgentRunDO extends WAgentBaseDO {

    private String id;
    private String questionId;
    private String missionId;
    private String parentRunId;
    private String previousRunId;
    private String planSource;
    private String planJson;
    private String status;
    private Integer automationCeiling;
    private String initiatedBy;
    private String idempotencyKey;
    private String budgetJson;
    private String consumptionJson;
    private String readinessId;
    private String degradationsJson;
    private String situationSnapshotJson;
    private String resultRefText;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }

    public String getMissionId() { return missionId; }
    public void setMissionId(String missionId) { this.missionId = missionId; }

    public String getParentRunId() { return parentRunId; }
    public void setParentRunId(String parentRunId) { this.parentRunId = parentRunId; }

    public String getPreviousRunId() { return previousRunId; }
    public void setPreviousRunId(String previousRunId) { this.previousRunId = previousRunId; }

    public String getPlanSource() { return planSource; }
    public void setPlanSource(String planSource) { this.planSource = planSource; }

    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAutomationCeiling() { return automationCeiling; }
    public void setAutomationCeiling(Integer automationCeiling) { this.automationCeiling = automationCeiling; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getBudgetJson() { return budgetJson; }
    public void setBudgetJson(String budgetJson) { this.budgetJson = budgetJson; }

    public String getConsumptionJson() { return consumptionJson; }
    public void setConsumptionJson(String consumptionJson) { this.consumptionJson = consumptionJson; }

    public String getReadinessId() { return readinessId; }
    public void setReadinessId(String readinessId) { this.readinessId = readinessId; }

    public String getDegradationsJson() { return degradationsJson; }
    public void setDegradationsJson(String degradationsJson) { this.degradationsJson = degradationsJson; }

    public String getSituationSnapshotJson() { return situationSnapshotJson; }
    public void setSituationSnapshotJson(String situationSnapshotJson) { this.situationSnapshotJson = situationSnapshotJson; }

    public String getResultRefText() { return resultRefText; }
    public void setResultRefText(String resultRefText) { this.resultRefText = resultRefText; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
