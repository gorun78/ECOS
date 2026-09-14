package com.chinacreator.gzcm.workspace.scenario;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 场景保存 DTO — 创建/更新业务场景的入参（PMO-50，强类型替代 Map）。
 */
public class ScenarioSaveDTO {

    /** 场景名称（必填，≤255） */
    private String name;
    /** 场景描述（可选） */
    private String description;
    /** 业务目标（可选，缺省取 description） */
    private String businessGoal;
    /** 归属部门（可选） */
    private String department;
    /** 优先级（可选，默认 MEDIUM，枚举：CRITICAL/HIGH/MEDIUM/LOW） */
    private String priority;
    /** 状态（可选，创建默认 DRAFT，枚举：DRAFT/ACTIVE/COMPLETED/SUSPENDED） */
    private String status;
    /** 预算（可选） */
    private String budget;
    /** 安全指标目标（可选，0~1） */
    private BigDecimal safetyIndexTarget;
    /** 当前安全指标（可选，0~1） */
    private BigDecimal actualSafetyIndex;
    /** 附加指标（可选，键值对如 integrityScore→92） */
    private Map<String, Object> metrics;
    /** 绑定列表（可选，null = 不改动绑定；空列表 = 清空绑定） */
    private List<ScenarioBindingItem> bindings;

    /** 绑定条目 — 六类资源绑定 */
    public static class ScenarioBindingItem {
        /** 绑定类型（必填，枚举：DATASET/OBJECT_TYPE/KNOWLEDGE_BASE/AI_AGENT/SECURITY_POLICY/INTERFACE） */
        private String bindingType;
        /** 目标资源标识（必填，≤255） */
        private String targetRef;
        /** 备注（可选） */
        private String remark;

        public String getBindingType() { return bindingType; }
        public void setBindingType(String bindingType) { this.bindingType = bindingType; }
        public String getTargetRef() { return targetRef; }
        public void setTargetRef(String targetRef) { this.targetRef = targetRef; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }
    public BigDecimal getSafetyIndexTarget() { return safetyIndexTarget; }
    public void setSafetyIndexTarget(BigDecimal safetyIndexTarget) { this.safetyIndexTarget = safetyIndexTarget; }
    public BigDecimal getActualSafetyIndex() { return actualSafetyIndex; }
    public void setActualSafetyIndex(BigDecimal actualSafetyIndex) { this.actualSafetyIndex = actualSafetyIndex; }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    public List<ScenarioBindingItem> getBindings() { return bindings; }
    public void setBindings(List<ScenarioBindingItem> bindings) { this.bindings = bindings; }
}
