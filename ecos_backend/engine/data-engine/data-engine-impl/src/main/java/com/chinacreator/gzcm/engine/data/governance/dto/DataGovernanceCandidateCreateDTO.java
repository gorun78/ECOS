package com.chinacreator.gzcm.engine.data.governance.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 数据治理候选创建入参（《补充详细设计 v1.0》§7.2 候选协议）。
 *
 * <p>必带字段（§7.5）：{@code idempotencyKey}（租户内唯一，重试不重复创建）、
 * {@code tenantId}（缺省取 UserContext）、{@code createdBy}（缺省取登录态）、
 * {@code traceId}（缺省取 MDC）、{@code sourceRunId}。
 * {@code sourceLlm=true} 时置信度强制 UNCALIBRATED_MODEL_ESTIMATE（§7.2 置信度规则）。</p>
 */
public class DataGovernanceCandidateCreateDTO {

    /** 候选类型（V272 列注释词表）：FIELD_SEMANTIC / FIELD_MAPPING / DQ_RULE / CONVERSION / DATA_CHANGE_PROPOSAL / OTHER。 */
    private String candidateType;
    /** 受影响资产/字段引用（asset-001.field-17 形态）。 */
    private String targetRef;
    /** 候选正文（§7.2 candidate.proposal 对象）。 */
    private Object proposal;
    /** 证据引用（profile-001 / ontology-ref-4 / 字段命中清单等）。 */
    private List<String> evidenceRefs;
    /** LLM 自报置信度原文（如「90% 确信」）——提供时 sourceLlm 视为 true。 */
    private String confidenceLlmText;
    /** 数值置信度 0..1（与 confidenceLlmText 二选一，数值优先）。 */
    private BigDecimal confidenceValue;
    /** 请求方声明 kind：STATISTICAL / CALIBRATED_MODEL / MODEL_ESTIMATE。 */
    private String confidenceKind;
    /** 请求方声明已校准（必须走 calibration 门禁才可为 true；sourceLlm=true 时被强制 false）。 */
    private Boolean calibrated;
    /** 前提假设清单。 */
    private List<String> assumptions;
    /** 与权威资产的冲突声明。 */
    private List<Map<String, Object>> conflicts;
    /** 影响面（affectedAssets / breakingChange，§7.2 impact）。 */
    private Map<String, Object> impact;
    /** DQ_PREVIEW 只读预览通过率（0..1）。 */
    private Double previewPassRate;
    /** DATA_CHANGE_PROPOSAL 必带回滚计划（§7.2：无回滚计划禁进 PENDING_APPROVAL）。 */
    private String rollbackPlan;
    /** 生成该候选的 Tool Contract 引用（V270 词典）。 */
    private String toolContractRef;
    /** PENDING_APPROVAL→APPROVED 必填的审批记录引用。 */
    private String approvalRef;
    /** 触发候选的 W Agent Run 引用（§7.5 sourceRunId）。 */
    private String sourceRunId;
    /** traceId（§7.5；缺省取 MDC）。 */
    private String traceId;
    /** 幂等键（§7.5 租户内唯一；重试返回既有记录而非重复创建）。 */
    private String idempotencyKey;
    /** 租户 ID（缺省取 UserContext.tenantId → "default"）。 */
    private String tenantId;
    /** 组织 ID（V272 org_id，可空）。 */
    private String orgId;
    /** 操作者（缺省取 UserContext.username → "system"）。 */
    private String createdBy;
    /** 域（缺省 default）。 */
    private String domain;
    /** 版本号（缺省 v1，V272 version_no NOT NULL）。 */
    private String versionNo;

    public String getCandidateType() {
        return candidateType;
    }

    public void setCandidateType(String candidateType) {
        this.candidateType = candidateType;
    }

    public String getTargetRef() {
        return targetRef;
    }

    public void setTargetRef(String targetRef) {
        this.targetRef = targetRef;
    }

    public Object getProposal() {
        return proposal;
    }

    public void setProposal(Object proposal) {
        this.proposal = proposal;
    }

    public List<String> getEvidenceRefs() {
        return evidenceRefs;
    }

    public void setEvidenceRefs(List<String> evidenceRefs) {
        this.evidenceRefs = evidenceRefs;
    }

    public String getConfidenceLlmText() {
        return confidenceLlmText;
    }

    public void setConfidenceLlmText(String confidenceLlmText) {
        this.confidenceLlmText = confidenceLlmText;
    }

    public BigDecimal getConfidenceValue() {
        return confidenceValue;
    }

    public void setConfidenceValue(BigDecimal confidenceValue) {
        this.confidenceValue = confidenceValue;
    }

    public String getConfidenceKind() {
        return confidenceKind;
    }

    public void setConfidenceKind(String confidenceKind) {
        this.confidenceKind = confidenceKind;
    }

    public Boolean getCalibrated() {
        return calibrated;
    }

    public void setCalibrated(Boolean calibrated) {
        this.calibrated = calibrated;
    }

    public List<String> getAssumptions() {
        return assumptions;
    }

    public void setAssumptions(List<String> assumptions) {
        this.assumptions = assumptions;
    }

    public List<Map<String, Object>> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<Map<String, Object>> conflicts) {
        this.conflicts = conflicts;
    }

    public Map<String, Object> getImpact() {
        return impact;
    }

    public void setImpact(Map<String, Object> impact) {
        this.impact = impact;
    }

    public Double getPreviewPassRate() {
        return previewPassRate;
    }

    public void setPreviewPassRate(Double previewPassRate) {
        this.previewPassRate = previewPassRate;
    }

    public String getRollbackPlan() {
        return rollbackPlan;
    }

    public void setRollbackPlan(String rollbackPlan) {
        this.rollbackPlan = rollbackPlan;
    }

    public String getToolContractRef() {
        return toolContractRef;
    }

    public void setToolContractRef(String toolContractRef) {
        this.toolContractRef = toolContractRef;
    }

    public String getApprovalRef() {
        return approvalRef;
    }

    public void setApprovalRef(String approvalRef) {
        this.approvalRef = approvalRef;
    }

    public String getSourceRunId() {
        return sourceRunId;
    }

    public void setSourceRunId(String sourceRunId) {
        this.sourceRunId = sourceRunId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(String versionNo) {
        this.versionNo = versionNo;
    }
}
