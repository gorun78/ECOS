package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.time.LocalDateTime;

/**
 * V230 {@code ecos_ai.ecos_wagent_readiness} DIKC 就绪评估主表 DO（REQ-WAG-08；item/gap 见 V231/V232）。
 * <p>grade/layer_* 由服务端确定性计算（禁 LLM 产出定级，F10-08）；被 Decision 引用的评估
 * {@code is_pinned=1} 不参与重评清理（F10-07）。</p>
 */
public class WAgentReadinessDO extends WAgentBaseDO {

    private String id;
    private String questionId;
    private String runId;
    private String profileId;
    private String profileVersion;
    private String grade;
    private String layerD;
    private String layerI;
    private String layerK;
    private String layerC;
    private String layerSummaryJson;
    private String degradationsJson;
    private LocalDateTime computedTime;
    private LocalDateTime validUntil;
    private String supersededBy;
    private Integer isPinned;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getProfileVersion() { return profileVersion; }
    public void setProfileVersion(String profileVersion) { this.profileVersion = profileVersion; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getLayerD() { return layerD; }
    public void setLayerD(String layerD) { this.layerD = layerD; }

    public String getLayerI() { return layerI; }
    public void setLayerI(String layerI) { this.layerI = layerI; }

    public String getLayerK() { return layerK; }
    public void setLayerK(String layerK) { this.layerK = layerK; }

    public String getLayerC() { return layerC; }
    public void setLayerC(String layerC) { this.layerC = layerC; }

    public String getLayerSummaryJson() { return layerSummaryJson; }
    public void setLayerSummaryJson(String layerSummaryJson) { this.layerSummaryJson = layerSummaryJson; }

    public String getDegradationsJson() { return degradationsJson; }
    public void setDegradationsJson(String degradationsJson) { this.degradationsJson = degradationsJson; }

    public LocalDateTime getComputedTime() { return computedTime; }
    public void setComputedTime(LocalDateTime computedTime) { this.computedTime = computedTime; }

    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }

    public String getSupersededBy() { return supersededBy; }
    public void setSupersededBy(String supersededBy) { this.supersededBy = supersededBy; }

    public Integer getIsPinned() { return isPinned; }
    public void setIsPinned(Integer isPinned) { this.isPinned = isPinned; }
}
