package com.chinacreator.gzcm.engine.kb.profile;

import java.util.List;

/**
 * 历史画像 list 行 VO（GET /api/v1/knowledge/profiles）。
 *
 * @author ECOS KB Team
 */
public class ProfileVO {

    private String id;
    private String profileKey;
    private String metricCode;
    private String groupDimsJson;
    private String windowFrom;
    private String windowTo;
    private String statsRef;
    private String ciLevel;
    private String statsMethod;
    private String confidence;
    private String degradeFrom;
    private List<String> degradeChain;
    private String sourceQueryRef;
    private String profileVersion;
    private String status;
    private String approvedBy;
    private String taskId;
    private String traceId;
    private String domain;
    private java.util.Date createTime;
    private java.util.Date updateTime;

    public ProfileVO() {
    }

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
    public String getStatsRef() { return statsRef; }
    public void setStatsRef(String statsRef) { this.statsRef = statsRef; }
    public String getCiLevel() { return ciLevel; }
    public void setCiLevel(String ciLevel) { this.ciLevel = ciLevel; }
    public String getStatsMethod() { return statsMethod; }
    public void setStatsMethod(String statsMethod) { this.statsMethod = statsMethod; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public String getDegradeFrom() { return degradeFrom; }
    public void setDegradeFrom(String degradeFrom) { this.degradeFrom = degradeFrom; }
    public List<String> getDegradeChain() { return degradeChain; }
    public void setDegradeChain(List<String> degradeChain) { this.degradeChain = degradeChain; }
    public String getSourceQueryRef() { return sourceQueryRef; }
    public void setSourceQueryRef(String sourceQueryRef) { this.sourceQueryRef = sourceQueryRef; }
    public String getProfileVersion() { return profileVersion; }
    public void setProfileVersion(String profileVersion) { this.profileVersion = profileVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public java.util.Date getCreateTime() { return createTime; }
    public void setCreateTime(java.util.Date createTime) { this.createTime = createTime; }
    public java.util.Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(java.util.Date updateTime) { this.updateTime = updateTime; }
}
