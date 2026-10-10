package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.math.BigDecimal;

/**
 * V229 {@code ecos_ai.ecos_wagent_question} 业务问题 DO（意图识别与槽位承载，REQ-WAG-05/06）。
 * <p>{@code intent} 与 Goal.goal_type 共用 {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent}
 * 单源（禁二套词表）；{@code intent_confidence < 0.70} 不得建 Run（应用层校验，DB 仅 CHECK 值域）。</p>
 */
public class WAgentQuestionDO extends WAgentBaseDO {

    private String id;
    private String missionId;
    private String rawText;
    private String intent;
    private BigDecimal intentConfidence;
    private String slotsJson;
    private String dependsOnJson;
    private String status;
    private String latestRunId;
    private String latestGrade;
    private String answerSummaryText;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMissionId() { return missionId; }
    public void setMissionId(String missionId) { this.missionId = missionId; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public BigDecimal getIntentConfidence() { return intentConfidence; }
    public void setIntentConfidence(BigDecimal intentConfidence) { this.intentConfidence = intentConfidence; }

    public String getSlotsJson() { return slotsJson; }
    public void setSlotsJson(String slotsJson) { this.slotsJson = slotsJson; }

    public String getDependsOnJson() { return dependsOnJson; }
    public void setDependsOnJson(String dependsOnJson) { this.dependsOnJson = dependsOnJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLatestRunId() { return latestRunId; }
    public void setLatestRunId(String latestRunId) { this.latestRunId = latestRunId; }

    public String getLatestGrade() { return latestGrade; }
    public void setLatestGrade(String latestGrade) { this.latestGrade = latestGrade; }

    public String getAnswerSummaryText() { return answerSummaryText; }
    public void setAnswerSummaryText(String answerSummaryText) { this.answerSummaryText = answerSummaryText; }
}
