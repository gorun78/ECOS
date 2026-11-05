package com.chinacreator.gzcm.engine.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * AIP Pipeline 行（内存 store 的唯一行模型）。
 *
 * <p>键序：{@code id/name/description/steps/status/createdAt/updatedAt[/lastExecutedAt]}；
 * {@code steps} 为步骤定义数组（自由结构，原样透传）；{@code lastExecutedAt} 仅执行后出现（NON_NULL）。</p>
 */
@JsonPropertyOrder({"id", "name", "description", "steps", "status", "createdAt", "updatedAt",
        "lastExecutedAt"})
public record AipPipelineVO(
        String id,
        String name,
        String description,
        Object steps,
        String status,
        String createdAt,
        String updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String lastExecutedAt) {

    /** 部分更新：仅覆盖出现的键。 */
    public AipPipelineVO withUpdated(String newName, String newDescription, Object newSteps,
                                     String newStatus, String newUpdatedAt) {
        return new AipPipelineVO(id,
                newName != null ? newName : name,
                newDescription != null ? newDescription : description,
                newSteps != null ? newSteps : steps,
                newStatus != null ? newStatus : status,
                createdAt, newUpdatedAt, lastExecutedAt);
    }

    /** 执行后回写：状态 running/completed + 最近执行时刻。 */
    public AipPipelineVO withStatusAndExecution(String newStatus, String executedAt) {
        return new AipPipelineVO(id, name, description, steps, newStatus, createdAt,
                updatedAt, executedAt);
    }
}
