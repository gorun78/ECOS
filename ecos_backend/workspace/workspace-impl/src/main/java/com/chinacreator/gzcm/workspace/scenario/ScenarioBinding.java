package com.chinacreator.gzcm.workspace.scenario;

import java.time.LocalDateTime;

/**
 * 场景绑定 PO — 场景六类资源绑定（PMO-50）。
 * <p>对应表 {@code ecos_scenario_binding}。</p>
 */
public class ScenarioBinding {

    /** 主键 */
    private String id;
    /** 所属场景 id */
    private String scenarioId;
    /** 绑定类型：DATASET / OBJECT_TYPE / KNOWLEDGE_BASE / AI_AGENT / SECURITY_POLICY / INTERFACE */
    private String bindingType;
    /** 目标资源标识（数据集 id / 对象类型 code / 知识库 id 等） */
    private String targetRef;
    /** v2.0 真 PG 主键（替代 targetRef 字符串别名） */
    private String targetId;
    /** v2.0 真资源类型：DATASOURCE|ONTOLOGY_ENTITY|KNOWLEDGE_ARTICLE|AGENT_PROFILE|SECURITY_POLICY|INTERFACE_REF */
    private String targetType;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
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
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
