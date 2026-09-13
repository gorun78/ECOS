package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流审批 DTO（approve / reject 共用） — T16-2 强类型入参。
 *
 * <p>字段对应 {@code WorkflowApprovalService.approve / reject} 实际消费的业务语义：
 * {@code userId} 缺失时回退到 task 的 assignee；{@code formData} 动态传入。
 *
 * <p>使用 {@code @JsonAnyGetter/@JsonAnySetter} 兜底未知字段，
 * 避免前端传多余 key 时反序列化失败（兼容既有契约）。
 */
@Data
public class OntologyWorkflowApprovalDTO {

    /** 审批人 ID（缺省回退到 task assignee） */
    private String userId;

    /** 审批意见 */
    private String opinion;

    /** 表单数据（透传到 OPA / 持久层） */
    private Object formData;

    /**
     * 未知字段透传（兼容旧契约里其他 key）。
     * 不暴露给 JSON 序列化出参（仅 setter / getter 接收前端字段）。
     */
    private final Map<String, Object> extras = new LinkedHashMap<>();

    @JsonAnySetter
    public void put(String key, Object value) {
        if ("userId".equals(key) || "opinion".equals(key) || "formData".equals(key)) {
            return;
        }
        this.extras.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtras() {
        return this.extras;
    }
}
