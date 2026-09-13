package com.chinacreator.gzcm.workspace.scenario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 场景视图 VO — 场景工作台列表/详情出参（PMO-50）。
 * <p>在实体字段之外聚合 {@code bindings}（按类型分组）与解析后的 {@code metrics}，
 * 保持与旧内存实现的响应形状兼容（前端既有消费方零改动）。</p>
 */
public class ScenarioVO {

    /** 场景主键 */
    private String id;
    /** 场景名称 */
    private String name;
    /** 场景描述 */
    private String description;
    /** 业务目标 */
    private String businessGoal;
    /** 归属部门 */
    private String department;
    /** 优先级：CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;
    /** 状态：DRAFT / ACTIVE / COMPLETED / SUSPENDED */
    private String status;
    /** 预算 */
    private String budget;
    /** 安全指标目标（0~1） */
    private BigDecimal safetyIndexTarget;
    /** 当前安全指标（0~1） */
    private BigDecimal actualSafetyIndex;
    /** 附加指标（integrityScore 等，JSONB 解析后） */
    private Map<String, Object> metrics;
    /** 创建时间 ISO-8601 字符串 */
    private String createdAt;
    /** 六类绑定（camelCase key：datasets/objectTypes/knowledgeBases/aiAgents/securityPolicies/interfaces） */
    private Map<String, List<String>> bindings;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Map<String, List<String>> getBindings() { return bindings; }
    public void setBindings(Map<String, List<String>> bindings) { this.bindings = bindings; }

    /** 把 PO 转 VO（不含 bindings/metrics 聚合，由 Service 填充） */
    public static ScenarioVO fromEntity(BusinessScenario e) {
        ScenarioVO vo = new ScenarioVO();
        vo.setId(e.getId());
        vo.setName(e.getName());
        vo.setDescription(e.getDescription());
        vo.setBusinessGoal(e.getBusinessGoal());
        vo.setDepartment(e.getDepartment());
        vo.setPriority(e.getPriority());
        vo.setStatus(e.getStatus());
        vo.setBudget(e.getBudget());
        vo.setSafetyIndexTarget(e.getSafetyIndexTarget());
        vo.setActualSafetyIndex(e.getActualSafetyIndex());
        if (e.getCreateTime() != null) {
            vo.setCreatedAt(e.getCreateTime().toString());
        }
        return vo;
    }

    /** 初始化空绑定容器（六类均默认空列表，稳定序列化形状） */
    public static Map<String, List<String>> emptyBindings() {
        Map<String, List<String>> m = new java.util.LinkedHashMap<>();
        m.put("datasets", new ArrayList<>());
        m.put("objectTypes", new ArrayList<>());
        m.put("knowledgeBases", new ArrayList<>());
        m.put("aiAgents", new ArrayList<>());
        m.put("securityPolicies", new ArrayList<>());
        m.put("interfaces", new ArrayList<>());
        return m;
    }
}
