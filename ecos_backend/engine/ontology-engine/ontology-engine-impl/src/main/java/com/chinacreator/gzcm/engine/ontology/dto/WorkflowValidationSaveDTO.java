package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流定义验证入参 DTO — T16-4。
 *
 * <p>对齐 {@code WorkflowController（/api/v1/ecos/workflows）.validateWorkflow}
 * 既有 body 契约（{@code WorkflowService.validateWorkflow(definition)} 实际消费）：
 * {@code id / name / mode / nodes / edges}；nodes / edges 动态结构
 * （T16-4: workflow 定义动态结构豁免）。
 *
 * <p>历史客户端可能透传任意 key，故以 {@code @JsonAnySetter/@JsonAnyGetter}
 * 全字段透传（与原先 Map 全字段行为一致），Controller 内经
 * Jackson {@code convertValue} 还原 Map 调旧 service。
 */
@Data
public class WorkflowValidationSaveDTO {

    /** workflow 定义全局 extra 字段透传（全 key，含声明字段） */
    private Map<String, Object> payload = new LinkedHashMap<>();

    @JsonAnySetter
    public void set(String key, Object value) {
        this.payload.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getPayload() {
        return this.payload;
    }
}
