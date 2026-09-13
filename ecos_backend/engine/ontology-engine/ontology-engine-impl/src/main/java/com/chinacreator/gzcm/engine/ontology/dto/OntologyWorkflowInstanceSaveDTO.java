package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 工作流实例 startInstance DTO — T16-2。
 *
 * <p>字段对应 {@code WorkflowInstanceService.startInstance(workflowId, body)} 实际消费的业务语义：
 * {@code workflowId} 必填；{@code triggerType} / {@code userId} / {@code objectId} 可选；
 * {@code variables} 动态透传（剩余字段走 {@code @JsonAnyGetter / @JsonAnySetter} 兜底，
 * 避免前端 payload 字段被丢，与原先 Map 全字段透传行为一致）。
 *
 * <p>service 内部 {@code context = toJson(body)}（整个 body 序列化到 context 列），
 * 因此 extras 必须随 body 一起透传到 service（通过 convertValue → Map 路径实现）。
 */
@Data
public class OntologyWorkflowInstanceSaveDTO {

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
    private java.util.Map<String, Object> extras = new java.util.LinkedHashMap<>();

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void put(String key, Object value) {
        if ("workflowId".equals(key) || "triggerType".equals(key) || "userId".equals(key)
            || "objectId".equals(key) || "variables".equals(key)) {
            return;
        }
        this.extras.put(key, value);
    }

    @com.fasterxml.jackson.annotation.JsonAnyGetter
    public java.util.Map<String, Object> getExtras() {
        return this.extras;
    }
}
