package com.chinacreator.gzcm.runtime.llm.usage;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * LLM token 计量实体（V244 public.ecos_runtime_llm_usage，详细设计-00 §6.2）。
 * <p>
 * 金额列 {@code cost_amount} 唯一形态 {@code NUMERIC(18,6)}（ST03 白名单形态）；
 * MC01 主键应用侧 UUID。
 */
public class LlmUsageEntity {

    /** MC01：应用侧 UUID，DDL 无默认值 */
    private String id;
    private String provider;
    private String model;
    /** chat|embedding|rerank */
    private String purpose;
    private Integer promptTokens;
    private Integer completionTokens;
    /** NUMERIC(18,6) — 金额唯一形态 */
    private BigDecimal costAmount;
    private String callerModule;
    private String callerId;
    private String scenarioId;
    private String traceId;
    private Timestamp createTime;
    private Timestamp updateTime;
    private String versionNo;
    private Short isDeleted;
    private String domain;
    private String tenantId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public BigDecimal getCostAmount() {
        return costAmount;
    }

    public void setCostAmount(BigDecimal costAmount) {
        this.costAmount = costAmount;
    }

    public String getCallerModule() {
        return callerModule;
    }

    public void setCallerModule(String callerModule) {
        this.callerModule = callerModule;
    }

    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public Timestamp getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Timestamp updateTime) {
        this.updateTime = updateTime;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(String versionNo) {
        this.versionNo = versionNo;
    }

    public Short getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Short isDeleted) {
        this.isDeleted = isDeleted;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
