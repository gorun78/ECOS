package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.time.LocalDateTime;

/**
 * V232 {@code ecos_ai.ecos_wagent_readiness_gap} 就绪缺口 DO（缺口·解锁值·补齐循环，REQ-WAG-09）。
 * <p>补齐循环走任务中心 runtime-task 单一来源（{@code type=task} 经 ITaskManagementService，禁自建调度表）；
 * 复评 refillRound ≤3。</p>
 */
public class WAgentReadinessGapDO extends WAgentBaseDO {

    private String id;
    private String assessmentId;
    private String layer;
    private String severity;
    private String descriptionText;
    private String impactText;
    private String suggestedActionText;
    private String suggestedOwner;
    private String effortEstimate;
    private String unlockFrom;
    private String unlockTo;
    private String status;
    private LocalDateTime resolvedTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getDescriptionText() { return descriptionText; }
    public void setDescriptionText(String descriptionText) { this.descriptionText = descriptionText; }

    public String getImpactText() { return impactText; }
    public void setImpactText(String impactText) { this.impactText = impactText; }

    public String getSuggestedActionText() { return suggestedActionText; }
    public void setSuggestedActionText(String suggestedActionText) { this.suggestedActionText = suggestedActionText; }

    public String getSuggestedOwner() { return suggestedOwner; }
    public void setSuggestedOwner(String suggestedOwner) { this.suggestedOwner = suggestedOwner; }

    public String getEffortEstimate() { return effortEstimate; }
    public void setEffortEstimate(String effortEstimate) { this.effortEstimate = effortEstimate; }

    public String getUnlockFrom() { return unlockFrom; }
    public void setUnlockFrom(String unlockFrom) { this.unlockFrom = unlockFrom; }

    public String getUnlockTo() { return unlockTo; }
    public void setUnlockTo(String unlockTo) { this.unlockTo = unlockTo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getResolvedTime() { return resolvedTime; }
    public void setResolvedTime(LocalDateTime resolvedTime) { this.resolvedTime = resolvedTime; }
}
