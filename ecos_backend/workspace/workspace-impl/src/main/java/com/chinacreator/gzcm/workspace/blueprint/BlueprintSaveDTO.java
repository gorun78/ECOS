package com.chinacreator.gzcm.workspace.blueprint;

import java.util.List;
import java.util.Map;

/**
 * 创建蓝图 Draft 入参（§6.2 逻辑模型的请求侧投影）。
 *
 * <p>结构化子段以 Map/List 承载（§6.2：dependencies/capabilities/gaps/risks/tests 整体逻辑结构），
 * 服务侧序列化为 JSON 文本落 *_text 列；content_json = 完整逻辑模型快照（发布冻结体）。</p>
 */
public class BlueprintSaveDTO {

    /** 必填：逻辑引用 workspace 场景主表（scenario_id NOT NULL）。 */
    private String scenarioId;
    /** 必填：业务目标陈述（goal.statement）。 */
    private String goalStatement;
    /** 成功条件（goal.successCriteria 字符串数组）。 */
    private List<String> successCriteria;
    /** 范围（scope JSON 对象：tenantId/periods/projectIds/caliberId 等）。 */
    private Map<String, Object> scope;
    /** 依赖（§6.2 dependencies 数组：type/id/required/versionConstraint/status）。 */
    private List<Map<String, Object>> dependencies;
    /** 能力需求（§6.2 capabilities 数组：capabilityId/contractVersion/required）。 */
    private List<Map<String, Object>> capabilities;
    /** 风险（§6.2 risks 数组）。 */
    private List<Map<String, Object>> risks;
    /** 测试用例引用（§6.2 tests 数组：P0/Pn 分级）。 */
    private List<Map<String, Object>> tests;
    /** 是否需审批（approval.required）。 */
    private boolean approvalRequired;
    /** 审批策略引用（approval.policyRef，security 裁决侧只存引用）。 */
    private String approvalPolicyRef;
    /** 关联 Context Pack 摘要（§5.2 contextDigest，可后补）。 */
    private String contextDigest;
    /** 建稿原因/说明（迁移审计留痕用）。 */
    private String note;

    public String getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
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

    public String getApprovalPolicyRef() {
        return approvalPolicyRef;
    }

    public void setApprovalPolicyRef(String approvalPolicyRef) {
        this.approvalPolicyRef = approvalPolicyRef;
    }

    public String getContextDigest() {
        return contextDigest;
    }

    public void setContextDigest(String contextDigest) {
        this.contextDigest = contextDigest;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
