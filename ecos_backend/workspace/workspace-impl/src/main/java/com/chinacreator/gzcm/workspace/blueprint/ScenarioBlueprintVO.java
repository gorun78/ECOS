package com.chinacreator.gzcm.workspace.blueprint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 蓝图读模型（响应侧 VO）：状态/摘要/结构化子段投影 + allowedTransitions（前端按钮态唯一来源）。
 * 载荷只带引用 ID 与结构化状态，禁带 Prompt 类正文（事件同纪律）。
 */
public class ScenarioBlueprintVO {

    private String id;
    private String scenarioId;
    private int version;
    private String status;
    private String goalStatement;
    private List<String> successCriteria;
    private Map<String, Object> scope;
    private List<Map<String, Object>> dependencies;
    private List<Map<String, Object>> capabilities;
    private List<Map<String, Object>> gaps;
    private List<Map<String, Object>> risks;
    private List<Map<String, Object>> tests;
    private boolean approvalRequired;
    private String approvalRef;
    private String artifactDigest;
    private String contextDigest;
    private String traceId;
    private String actor;
    private String transitionReason;
    private LocalDateTime releasedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<String> allowedTransitions;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGoalStatement() {
        return goalStatement;
    }

    public void setGoalStatement(String goalStatement) {
        this.goalStatement = goalStatement;
    }

    public List<String> getSuccessCriteria() {
        return successCriteria;
    }

    public void setSuccessCriteria(List<String> successCriteria) {
        this.successCriteria = successCriteria;
    }

    public Map<String, Object> getScope() {
        return scope;
    }

    public void setScope(Map<String, Object> scope) {
        this.scope = scope;
    }

    public List<Map<String, Object>> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<Map<String, Object>> dependencies) {
        this.dependencies = dependencies;
    }

    public List<Map<String, Object>> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<Map<String, Object>> capabilities) {
        this.capabilities = capabilities;
    }

    public List<Map<String, Object>> getGaps() {
        return gaps;
    }

    public void setGaps(List<Map<String, Object>> gaps) {
        this.gaps = gaps;
    }

    public List<Map<String, Object>> getRisks() {
        return risks;
    }

    public void setRisks(List<Map<String, Object>> risks) {
        this.risks = risks;
    }

    public List<Map<String, Object>> getTests() {
        return tests;
    }

    public void setTests(List<Map<String, Object>> tests) {
        this.tests = tests;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public String getApprovalRef() {
        return approvalRef;
    }

    public void setApprovalRef(String approvalRef) {
        this.approvalRef = approvalRef;
    }

    public String getArtifactDigest() {
        return artifactDigest;
    }

    public void setArtifactDigest(String artifactDigest) {
        this.artifactDigest = artifactDigest;
    }

    public String getContextDigest() {
        return contextDigest;
    }

    public void setContextDigest(String contextDigest) {
        this.contextDigest = contextDigest;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getTransitionReason() {
        return transitionReason;
    }

    public void setTransitionReason(String transitionReason) {
        this.transitionReason = transitionReason;
    }

    public LocalDateTime getReleasedTime() {
        return releasedTime;
    }

    public void setReleasedTime(LocalDateTime releasedTime) {
        this.releasedTime = releasedTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public List<String> getAllowedTransitions() {
        return allowedTransitions;
    }

    public void setAllowedTransitions(List<String> allowedTransitions) {
        this.allowedTransitions = allowedTransitions;
    }
}
