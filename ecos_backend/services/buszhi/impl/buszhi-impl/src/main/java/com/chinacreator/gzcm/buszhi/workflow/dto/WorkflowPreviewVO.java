package com.chinacreator.gzcm.buszhi.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 工作流预览展开结果 VO — 强类型返回。
 * （溯源：ontology-engine-impl T16-4 等价移植，PMO-57 T2 迁入 buszhi 服务层。）
 *
 * <p>对齐 {@code WorkflowService.previewWorkflow} 既有 Map 输出：
 * {@code workflowId / name / expandedSteps / estimatedPath / context}。
 * {@code expandedSteps} 为执行步迹、{@code context} 为调用方传入上下文，
 * 均为动态结构（workflow 实例运行时 payload 动态结构豁免），
 * 保持 {@link Object} 类型；{@code estimatedPath} 实为 dry-run 执行状态字符串。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowPreviewVO {

    /** 工作流 ID */
    private String workflowId;

    /** 工作流名称 */
    private String name;

    /** 展开执行步迹（动态结构，workflow 实例运行时 payload 动态结构豁免） */
    private Object expandedSteps;

    /** dry-run 执行状态（如 completed / failed） */
    private String estimatedPath;

    /** 调用方传入的预览上下文（动态结构，workflow 实例运行时 payload 动态结构豁免） */
    private Object context;

    public WorkflowPreviewVO() {
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getExpandedSteps() {
        return expandedSteps;
    }

    public void setExpandedSteps(Object expandedSteps) {
        this.expandedSteps = expandedSteps;
    }

    public String getEstimatedPath() {
        return estimatedPath;
    }

    public void setEstimatedPath(String estimatedPath) {
        this.estimatedPath = estimatedPath;
    }

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }
}
