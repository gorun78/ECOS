package com.chinacreator.gzcm.engine.kb.service.assumption;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * REQ-KB-03 情景假设 DO（F04-07，P0 新能力），对应 {@code ecos_knowledge.ecos_kb_assumption}（E.2 V177）。
 *
 * <p>状态机 {@code DRAFT → PENDING_APPROVAL → ACTIVE → [EXPIRED | SUPERSEDED]}，
 * 仅 ACTIVE 可被预测引用；DELTA 语义表达相对增量（{@code baseAssumptionId}）；
 * {@code reason} 必填；{@code evidenceRef} 缺失则 {@code isUnverified=1}（I-4 不可静默关闭）。
 */
public class KbAssumptionDO {

    private String id;
    private String assumptionKey;
    private String scenarioType;
    private String metricCode;
    private String dimScopeJson;
    private String valueType;
    private BigDecimal value;
    private String valueSemantic;
    private String baseAssumptionId;
    private String validFrom;
    private String validTo;
    private String reason;
    private String evidenceRef;
    private Integer isUnverified;
    private long expireAt;
    private String assumptionVersion;
    private String status;
    private String approvedBy;
    private long approvedTime;
    private String traceId;
    private String domain;
    private int versionNo;
    private int isDeleted;
    private String createBy;
    private String updateBy;
    private long createTime;
    private long updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAssumptionKey() { return assumptionKey; }
    public void setAssumptionKey(String assumptionKey) { this.assumptionKey = assumptionKey; }

    public String getScenarioType() { return scenarioType; }
    public void setScenarioType(String scenarioType) { this.scenarioType = scenarioType; }

    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }

    public String getDimScopeJson() { return dimScopeJson; }
    public void setDimScopeJson(String dimScopeJson) { this.dimScopeJson = dimScopeJson; }

    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public String getValueSemantic() { return valueSemantic; }
    public void setValueSemantic(String valueSemantic) { this.valueSemantic = valueSemantic; }

    public String getBaseAssumptionId() { return baseAssumptionId; }
    public void setBaseAssumptionId(String baseAssumptionId) { this.baseAssumptionId = baseAssumptionId; }

    public String getValidFrom() { return validFrom; }
    public void setValidFrom(String validFrom) { this.validFrom = validFrom; }

    public String getValidTo() { return validTo; }
    public void setValidTo(String validTo) { this.validTo = validTo; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }

    public Integer getIsUnverified() { return isUnverified; }
    public void setIsUnverified(Integer isUnverified) { this.isUnverified = isUnverified; }
    public void setIsUnverifiedBoolean(boolean b) { this.isUnverified = b ? 1 : 0; }
    public boolean isUnverifiedFlag() { return isUnverified != null && isUnverified != 0; }

    public long getExpireAt() { return expireAt; }
    public void setExpireAt(long expireAt) { this.expireAt = expireAt; }

    public String getAssumptionVersion() { return assumptionVersion; }
    public void setAssumptionVersion(String assumptionVersion) { this.assumptionVersion = assumptionVersion; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public long getApprovedTime() { return approvedTime; }
    public void setApprovedTime(long approvedTime) { this.approvedTime = approvedTime; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }

    public int getIsDeleted() { return isDeleted; }
    public void setIsDeleted(int isDeleted) { this.isDeleted = isDeleted; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }

    public long getUpdateTime() { return updateTime; }
    public void setUpdateTime(long updateTime) { this.updateTime = updateTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KbAssumptionDO that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
