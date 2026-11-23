package com.chinacreator.gzcm.workspace.definition;

import java.util.List;
import java.util.Map;

/**
 * 场景定义聚合读写 DTO（2026-11-21 场景工作台简化 v0.3）。
 *
 * <p>只服务新端点 {@code /api/v1/workspace/scenarios/{id}/definition}，不改变既有
 * {@code ScenarioSaveDTO} / {@code BlueprintSaveDTO} 契约。字段与前端「场景定义」四段一一对应：</p>
 * <ul>
 *   <li>basic：名称/目标/成功标准/部门/范围；</li>
 *   <li>bindings：数据/知识/能力/安全/接口（沿用六类，新增 ruleSets）；</li>
 *   <li>blueprint：蓝图内容（目标/范围/依赖/能力/风险/测试）；</li>
 *   <li>publish：可选审批策略与说明。</li>
 * </ul>
 */
public class ScenarioDefinitionDTO {

    /** 基本信息（name 必填；更新时 null 字段不改动）。 */
    private String name;
    private String description;
    private String businessGoal;
    private String department;
    private String priority;
    private String budget;
    private Map<String, Object> scope;
    private List<String> successCriteria;

    /** 绑定（null = 不改动；空列表 = 清空）。 */
    private List<ScenarioBindingItem> bindings;

    /** 蓝图草稿内容（null = 不改动）。 */
    private BlueprintDraft blueprint;

    /** 发布选项（仅 publish 端点使用）。 */
    private boolean approvalRequired;
    private String approvalPolicyRef;
    private String approvalRef;
    private String note;

    public static class ScenarioBindingItem {
        private String bindingType;
        private String targetRef;
        private String targetId;
        private String targetType;
        private String remark;

        public String getBindingType() { return bindingType; }
        public void setBindingType(String bindingType) { this.bindingType = bindingType; }
        public String getTargetRef() { return targetRef; }
        public void setTargetRef(String targetRef) { this.targetRef = targetRef; }
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class BlueprintDraft {
        private String goalStatement;
        private List<String> successCriteria;
        private Map<String, Object> scope;
        private List<Map<String, Object>> dependencies;
        private List<Map<String, Object>> capabilities;
        private List<Map<String, Object>> risks;
        private List<Map<String, Object>> tests;

        public String getGoalStatement() { return goalStatement; }
        public void setGoalStatement(String goalStatement) { this.goalStatement = goalStatement; }
        public List<String> getSuccessCriteria() { return successCriteria; }
        public void setSuccessCriteria(List<String> successCriteria) { this.successCriteria = successCriteria; }
        public Map<String, Object> getScope() { return scope; }
        public void setScope(Map<String, Object> scope) { this.scope = scope; }
        public List<Map<String, Object>> getDependencies() { return dependencies; }
        public void setDependencies(List<Map<String, Object>> dependencies) { this.dependencies = dependencies; }
        public List<Map<String, Object>> getCapabilities() { return capabilities; }
        public void setCapabilities(List<Map<String, Object>> capabilities) { this.capabilities = capabilities; }
        public List<Map<String, Object>> getRisks() { return risks; }
        public void setRisks(List<Map<String, Object>> risks) { this.risks = risks; }
        public List<Map<String, Object>> getTests() { return tests; }
        public void setTests(List<Map<String, Object>> tests) { this.tests = tests; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBusinessGoal() { return businessGoal; }
    public void setBusinessGoal(String businessGoal) { this.businessGoal = businessGoal; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }
    public Map<String, Object> getScope() { return scope; }
    public void setScope(Map<String, Object> scope) { this.scope = scope; }
    public List<String> getSuccessCriteria() { return successCriteria; }
    public void setSuccessCriteria(List<String> successCriteria) { this.successCriteria = successCriteria; }
    public List<ScenarioBindingItem> getBindings() { return bindings; }
    public void setBindings(List<ScenarioBindingItem> bindings) { this.bindings = bindings; }
    public BlueprintDraft getBlueprint() { return blueprint; }
    public void setBlueprint(BlueprintDraft blueprint) { this.blueprint = blueprint; }
    public boolean isApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(boolean approvalRequired) { this.approvalRequired = approvalRequired; }
    public String getApprovalPolicyRef() { return approvalPolicyRef; }
    public void setApprovalPolicyRef(String approvalPolicyRef) { this.approvalPolicyRef = approvalPolicyRef; }
    public String getApprovalRef() { return approvalRef; }
    public void setApprovalRef(String approvalRef) { this.approvalRef = approvalRef; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
