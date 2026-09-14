package com.chinacreator.gzcm.workspace.scenario;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业务场景 PO — 场景工作台（PMO-50）。
 * <p>对应表 {@code ecos_business_scenario}。手写 getter/setter（与 cognitive-engine 契约模型风格一致，本模块未引入 Lombok）。</p>
 */
public class BusinessScenario {

    /** 主键（sc001 / sc_xxxxxx），长度 64 兼容既有 id 方案 */
    private String id;
    /** 场景名称（必填） */
    private String name;
    /** 场景描述 */
    private String description;
    /** 业务目标（可与 description 相同，但语义独立便于将来分类） */
    private String businessGoal;
    /** 归属部门 */
    private String department;
    /** 优先级：CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;
    /** 状态：DRAFT / ACTIVE / COMPLETED / SUSPENDED */
    private String status;
    /** 预算（字符串保留单位，如"850万"） */
    private String budget;
    /** 安全指标目标（0~1） */
    private BigDecimal safetyIndexTarget;
    /** 当前安全指标（0~1） */
    private BigDecimal actualSafetyIndex;
    /** 附加指标 JSONB（integrityScore / mappingCompleteness 等） */
    private String metricsJson;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 创建人 */
    private String createBy;
    /** 更新人 */
    private String updateBy;
    /** 逻辑删除标记：0 = 正常, 1 = 已删除 */
    private Integer isDeleted;

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
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
}
