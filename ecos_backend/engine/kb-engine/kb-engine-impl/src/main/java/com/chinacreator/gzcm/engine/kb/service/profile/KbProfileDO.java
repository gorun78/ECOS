package com.chinacreator.gzcm.engine.kb.service.profile;

import java.math.BigDecimal;

/**
 * REQ-KB-02 历史画像持久行（映射 {@code ecos_knowledge.ecos_kb_profile}，V176）。
 *
 * <p>字段与 E.2 DDL 列一一对应；时间戳用 long epoch 毫秒（PG TIMESTAMP 于 SQL 端 {@code TO_TIMESTAMP} 转换）。
 */
public class KbProfileDO {

    private String id;
    private String profileKey;
    private String metricCode;
    private String groupDimsJson;
    private String windowFrom;
    private String windowTo;
    private int sampleCount;
    private BigDecimal missingRate;
    private BigDecimal p10;
    private BigDecimal p50;
    private BigDecimal p90;
    private BigDecimal meanValue;
    private BigDecimal ciLow;
    private BigDecimal ciHigh;
    private String ciLevel;
    private String statsMethod;
    private String confidence;
    private String degradeFrom;
    private String degradePathJson;
    private String applicableCondition;
    private String sourceQueryRef;
    private String profileVersion;
    private String status;
    private String approvedBy;
    private long approvedTime;
    private String taskId;
    private String traceId;
    private String domain;
    private int versionNo = 1;
    private int isDeleted;
    private String createBy;
    private String updateBy;
    private long createTime;
    private long updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProfileKey() { return profileKey; }
    public void setProfileKey(String profileKey) { this.profileKey = profileKey; }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public String getGroupDimsJson() { return groupDimsJson; }
    public void setGroupDimsJson(String groupDimsJson) { this.groupDimsJson = groupDimsJson; }
    public String getWindowFrom() { return windowFrom; }
    public void setWindowFrom(String windowFrom) { this.windowFrom = windowFrom; }
    public String getWindowTo() { return windowTo; }
    public void setWindowTo(String windowTo) { this.windowTo = windowTo; }
    public int getSampleCount() { return sampleCount; }
    public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }
    public BigDecimal getMissingRate() { return missingRate; }
    public void setMissingRate(BigDecimal missingRate) { this.missingRate = missingRate; }
    public BigDecimal getP10() { return p10; }
    public void setP10(BigDecimal p10) { this.p10 = p10; }
    public BigDecimal getP50() { return p50; }
    public void setP50(BigDecimal p50) { this.p50 = p50; }
    public BigDecimal getP90() { return p90; }
    public void setP90(BigDecimal p90) { this.p90 = p90; }
    public BigDecimal getMeanValue() { return meanValue; }
    public void setMeanValue(BigDecimal meanValue) { this.meanValue = meanValue; }
    public BigDecimal getCiLow() { return ciLow; }
    public void setCiLow(BigDecimal ciLow) { this.ciLow = ciLow; }
    public BigDecimal getCiHigh() { return ciHigh; }
    public void setCiHigh(BigDecimal ciHigh) { this.ciHigh = ciHigh; }
    public String getCiLevel() { return ciLevel; }
    public void setCiLevel(String ciLevel) { this.ciLevel = ciLevel; }
    public String getStatsMethod() { return statsMethod; }
    public void setStatsMethod(String statsMethod) { this.statsMethod = statsMethod; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public String getDegradeFrom() { return degradeFrom; }
    public void setDegradeFrom(String degradeFrom) { this.degradeFrom = degradeFrom; }
    public String getDegradePathJson() { return degradePathJson; }
    public void setDegradePathJson(String degradePathJson) { this.degradePathJson = degradePathJson; }
    public String getApplicableCondition() { return applicableCondition; }
    public void setApplicableCondition(String applicableCondition) { this.applicableCondition = applicableCondition; }
    public String getSourceQueryRef() { return sourceQueryRef; }
    public void setSourceQueryRef(String sourceQueryRef) { this.sourceQueryRef = sourceQueryRef; }
    public String getProfileVersion() { return profileVersion; }
    public void setProfileVersion(String profileVersion) { this.profileVersion = profileVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public long getApprovedTime() { return approvedTime; }
    public void setApprovedTime(long approvedTime) { this.approvedTime = approvedTime; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
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
}
