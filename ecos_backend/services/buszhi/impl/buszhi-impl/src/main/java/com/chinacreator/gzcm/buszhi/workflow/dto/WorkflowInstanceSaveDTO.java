package com.chinacreator.gzcm.buszhi.workflow.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流实例 startInstance DTO — 强类型入参。
 * （溯源：ontology-engine-impl T16-4 等价移植，PMO-57 T2 迁入 buszhi 服务层。）
 *
 * <p>字段对应 {@code WorkflowInstanceService.startInstance(workflowId, body)} 实际消费的业务语义：
 * {@code workflowId} 必填；{@code triggerType} / {@code userId} / {@code objectId} 可选；
 * 其余字段动态透传（{@code @JsonAnyGetter / @JsonAnySetter} 兜底，
 * 避免前端 payload 字段被丢，与原先 Map 全字段透传行为一致）。
 *
 * <p>service 内部 {@code context = toJson(body)}（整个 body 序列化到 context 列），
 * 因此 extras 必须随 body 一起透传到 service（通过 convertValue → Map 路径实现）。
 */
public class WorkflowInstanceSaveDTO {

    /** 工作流 ID（必填） */
    private String workflowId;

    /** 触发类型（默认 MANUAL） */
    private String triggerType;

    /** 触发者 ID */
    private String userId;

    /** 触发对象 ID */
    private String objectId;

    /** 实例业务变量 */
    private Object variables;

    /**
     * 未知字段透传（兼容旧 body 中其他 key）。
     * <p>字段名冲突的键（{@code workflowId/triggerType/userId/objectId/variables}）
     * 被 {@code put} 显式跳过，不重复。
     */
    private Map<String, Object> extras = new LinkedHashMap<>();

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public Object getVariables() {
        return variables;
    }

    public void setVariables(Object variables) {
        this.variables = variables;
    }

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void put(String key, Object value) {
        if ("workflowId".equals(key) || "triggerType".equals(key) || "userId".equals(key)
            || "objectId".equals(key) || "variables".equals(key)) {
            return;
        }
        this.extras.put(key, value);
    }

    @com.fasterxml.jackson.annotation.JsonAnyGetter
    public Map<String, Object> getExtras() {
        return this.extras;
    }
}
