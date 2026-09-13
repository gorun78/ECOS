package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 工作流测试运行结果 VO — T16-4 强类型返回。
 *
 * <p>对齐 {@code WorkflowEngine.WorkflowExecutionResult.toMap()} 输出：
 * {@code workflowId / status / executionTime / steps / context / activeNodes?}。
 * {@code steps} 元素为 {@code {nodeId, nodeType, label, status, time, result?, nextTarget?}}
 * 逐步追踪（result 为动态节点产物），{@code context} 为运行时上下文，
 * 二者均为动态结构（T16-4: workflow 实例运行时 payload 动态结构豁免），
 * 保持 {@link Object} 类型。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowTestVO {

    /** 工作流 ID */
    private String workflowId;

    /** 执行状态（completed / failed / ...） */
    private String status;

    /** 执行耗时（如 "12ms" / "1.2s"） */
    private String executionTime;

    /** 逐步执行追踪（动态结构，T16-4: workflow 实例运行时 payload 动态结构豁免） */
    private Object steps;

    /** 运行时上下文（动态结构，T16-4: workflow 实例运行时 payload 动态结构豁免） */
    private Object context;

    /** 当前活跃节点 ID 列表（仅执行中状态输出，T16-4: workflow 实例运行时 payload 动态结构豁免） */
    private Object activeNodes;
}
