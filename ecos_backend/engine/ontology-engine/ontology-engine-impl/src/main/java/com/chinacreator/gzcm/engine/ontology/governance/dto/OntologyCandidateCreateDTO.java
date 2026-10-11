package com.chinacreator.gzcm.engine.ontology.governance.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 本体 AI 候选创建入参(《ECOS 认知域与智能域补充详细设计 v1.0》§7.3 候选协议)。
 *
 * <p>对应 V273 {@code ecos_ontology_candidate} 表列语义。必带字段(§7.5):
 * {@code idempotencyKey}(租户内唯一,重试不重复创建),{@code tenantId}(缺省取
 * UserContext),{@code createdBy}(缺省取登录态),{@code traceId}(缺省取 MDC),
 * {@code sourceRunId}。
 *
 * <p>候选类型词表(V273):ENTITY / PROPERTY / RELATION / CONSTRAINT。
 * ENTITY / PROPERTY / RELATION 候选必须携带非空 {@code evidenceRefs}(§7.5 evidenceRequired)。</p>
 *
 * <p>置信度规则(§7.5):{@code confidenceLlmText} 非空或 {@code sourceLlm=true}
 * 时强制 UNCALIBRATED_MODEL_ESTIMATE + calibrated=false;声明 {@code calibrated=true}
 * 必须同时提供 {@code calibrationGateRef},否则强制 calibrated=false。</p>
 */
public class OntologyCandidateCreateDTO {

    /** 候选类型(V273 词表):ENTITY / PROPERTY / RELATION / CONSTRAINT。 */
    private String candidateType;
    /** 关系主体实体 code(subjectType;RELATION 候选必填,ENTITY 候选 = 自身 code)。 */
    private String subjectType;
    /** 关系谓词 code(RELATION 候选必填)。 */
    private String predicate;
    /** 关系客体实体 code(objectType;RELATION 候选必填,PROPERTY 候选可复用为所属实体)。 */
    private String objectType;
    /** 受影响对象引用(实体 code / 实体.属性 形态;所有类型必填)。 */
    private String targetRef;
    /** 候选正文(§7.3 candidate.proposal 对象,随选随证)。 */
    private Object proposal;
    /** 证据引用(profile-001 / 语料命中清单 / 既有关系引用等;ENTITY/PROPERTY/RELATION 必填)。 */
    private List<String> evidenceRefs;
    /** LLM 自报置信度原文(如 "90% 确信") -- 提供时视为 sourceLlm=true。 */
    private String confidenceLlmText;
    /** 数值置信度 0..1(与 confidenceLlmText 二选一,数值优先)。 */
    private BigDecimal confidenceValue;
    /** 请求方声明 kind:MODEL_ESTIMATE / UNCALIBRATED_MODEL_ESTIMATE / STATISTICAL。 */
    private String confidenceKind;
    /** 请求方声明已校准(必须走 calibration 门禁;无 calibrationGateRef 强制 false;LLM 自报强制 false)。 */
    private Boolean calibrated;
    /** calibration 门禁引用(声明 calibrated=true 时必填,无则强制 calibrated=false)。 */
    private String calibrationGateRef;
    /** 该置信度是否来自 LLM 自报(true 时强制未校准)。 */
    private Boolean sourceLlm;
    /** 与权威本体的冲突声明。 */
    private List<Map<String, Object>> conflicts;
    /** 兼容性声明(breakingChange / 影响面,§7.3 impact 等价物)。 */
    private Map<String, Object> compatibility;
    /** 生成该候选的 Tool Contract 引用。 */
    private String toolContractRef;
    /** PENDING_APPROVAL→APPROVED 必填的审批记录引用。 */
    private String approvalRef;
    /** 触发候选的 W Agent Run 引用(§7.5 sourceRunId;列表 batchId 过滤口径)。 */
    private String sourceRunId;
    /** traceId(§7.5;缺省 Header X-Request-Id → MDC → 新 UUID)。 */
    private String traceId;
    /** 幂等键(§7.5 租户内唯一;重试返回既有记录而非重复创建)。 */
    private String idempotencyKey;
    /** 租户 ID(缺省取 UserContext.tenantId → "default")。 */
    private String tenantId;
    /** 组织 ID(V273 org_id,可空)。 */
    private String orgId;
    /** 操作者(缺省取 UserContext.username → "system")。 */
    private String createdBy;
    /** 域(缺省 default)。 */
    private String domain;
    /** 版本号(缺省 v1,V273 version_no NOT NULL)。 */
    private String versionNo;

    public String getCandidateType() {
        return candidateType;
    }

    public void setCandidateType(String candidateType) {
        this.candidateType = candidateType;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getPredicate() {
        return predicate;
    }

    public void setPredicate(String predicate) {
        this.predicate = predicate;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
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

    public String getCalibrationGateRef() {
        return calibrationGateRef;
    }

    public void setCalibrationGateRef(String calibrationGateRef) {
        this.calibrationGateRef = calibrationGateRef;
    }

    public Boolean getSourceLlm() {
        return sourceLlm;
    }

    public void setSourceLlm(Boolean sourceLlm) {
        this.sourceLlm = sourceLlm;
    }

    public List<Map<String, Object>> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<Map<String, Object>> conflicts) {
        this.conflicts = conflicts;
    }

    public Map<String, Object> getCompatibility() {
        return compatibility;
    }

    public void setCompatibility(Map<String, Object> compatibility) {
        this.compatibility = compatibility;
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
