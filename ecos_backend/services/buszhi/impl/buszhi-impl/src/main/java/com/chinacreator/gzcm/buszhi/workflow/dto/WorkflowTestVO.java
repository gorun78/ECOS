package com.chinacreator.gzcm.buszhi.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 工作流测试运行结果 VO — 强类型返回。
 * （溯源：ontology-engine-impl T16-4 等价移植，PMO-57 T2 迁入 buszhi 服务层。）
 *
 * <p>对齐 {@code WorkflowEngine.WorkflowExecutionResult.toMap()} 输出：
 * {@code workflowId / status / executionTime / steps / context / activeNodes?}。
 * {@code steps} 元素为 {@code {nodeId, nodeType, label, status, time, result?, nextTarget?}}
 * 逐步追踪（result 为动态节点产物），{@code context} 为运行时上下文，
 * 二者均为动态结构（workflow 实例运行时 payload 动态结构豁免），
 * 保持 {@link Object} 类型。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowTestVO {

    /** 工作流 ID */
    private String workflowId;

    /** 执行状态（completed / failed / ...） */
    private String status;

    /** 执行耗时（如 "12ms" / "1.2s"） */
    private String executionTime;

    /** 逐步执行追踪（动态结构，workflow 实例运行时 payload 动态结构豁免） */
    private Object steps;

    /** 运行时上下文（动态结构，workflow 实例运行时 payload 动态结构豁免） */
    private Object context;

    /** 当前活跃节点 ID 列表（仅执行中状态输出，workflow 实例运行时 payload 动态结构豁免） */
    private Object activeNodes;

    public WorkflowTestVO() {
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(String executionTime) {
        this.executionTime = executionTime;
    }

    public Object getSteps() {
        return steps;
    }

    public void setSteps(Object steps) {
        this.steps = steps;
    }

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }

    public Object getActiveNodes() {
        return activeNodes;
    }

    public void setActiveNodes(Object activeNodes) {
        this.activeNodes = activeNodes;
    }
}
