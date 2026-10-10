package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * V238 {@code ecos_ai.ecos_wagent_candidate} 知识/资产候选 DO（四元校验与发布治理，R-40①）。
 * <p>词表单源（{@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums}）：
 * {@code candidate_type} = CandidateType 十二类（PRD-10 冻结名录，列宽 24 容得下最长
 * COGNITIVE_MODEL_DEF(19)）；{@code status} = CandidateStatus 状态机十值（F10-20；派生冻结草案，
 * R3-A 复核可修订）。{@code target_engine} 值域见
 * {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog#contractEngineCodesForDdlCheck()}。</p>
 * <p>{@code validation_*} 五段校验列 VARCHAR(12)（pass/fail/warn/skipped 词表随 R3-A 定版再设 CHECK，
 * R-15 纪律）。{@code confidence} NUMERIC(5,4)。发布唯一出口 Git：{@code published_git_ref}（R-40①）；
 * {@code engine_draft_ref_text} 只引用不复制（§6.3）。</p>
 */
public class WAgentCandidateDO extends WAgentBaseDO {

    private String id;
    private String candidateType;
    private String targetEngine;
    private String targetObjectRefJson;
    private String payloadJson;
    private String payloadHash;
    private String generatedByRun;
    private String generatedByStep;
    private BigDecimal confidence;
    private String basisJson;
    private String validationStructure;
    private String validationReference;
    private String validationConflict;
    private String validationImpact;
    private String validationRegression;
    private String status;
    private String batchId;
    private String engineDraftRefText;
    private String publishedRefText;
    private String publishedGitRef;
    private LocalDateTime expireTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCandidateType() { return candidateType; }
    public void setCandidateType(String candidateType) { this.candidateType = candidateType; }

    public String getTargetEngine() { return targetEngine; }
    public void setTargetEngine(String targetEngine) { this.targetEngine = targetEngine; }

    public String getTargetObjectRefJson() { return targetObjectRefJson; }
    public void setTargetObjectRefJson(String targetObjectRefJson) { this.targetObjectRefJson = targetObjectRefJson; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }

    public String getGeneratedByRun() { return generatedByRun; }
    public void setGeneratedByRun(String generatedByRun) { this.generatedByRun = generatedByRun; }

    public String getGeneratedByStep() { return generatedByStep; }
    public void setGeneratedByStep(String generatedByStep) { this.generatedByStep = generatedByStep; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public String getBasisJson() { return basisJson; }
    public void setBasisJson(String basisJson) { this.basisJson = basisJson; }

    public String getValidationStructure() { return validationStructure; }
    public void setValidationStructure(String validationStructure) { this.validationStructure = validationStructure; }

    public String getValidationReference() { return validationReference; }
    public void setValidationReference(String validationReference) { this.validationReference = validationReference; }

    public String getValidationConflict() { return validationConflict; }
    public void setValidationConflict(String validationConflict) { this.validationConflict = validationConflict; }

    public String getValidationImpact() { return validationImpact; }
    public void setValidationImpact(String validationImpact) { this.validationImpact = validationImpact; }

    public String getValidationRegression() { return validationRegression; }
    public void setValidationRegression(String validationRegression) { this.validationRegression = validationRegression; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getEngineDraftRefText() { return engineDraftRefText; }
    public void setEngineDraftRefText(String engineDraftRefText) { this.engineDraftRefText = engineDraftRefText; }

    public String getPublishedRefText() { return publishedRefText; }
    public void setPublishedRefText(String publishedRefText) { this.publishedRefText = publishedRefText; }

    public String getPublishedGitRef() { return publishedGitRef; }
    public void setPublishedGitRef(String publishedGitRef) { this.publishedGitRef = publishedGitRef; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
}
