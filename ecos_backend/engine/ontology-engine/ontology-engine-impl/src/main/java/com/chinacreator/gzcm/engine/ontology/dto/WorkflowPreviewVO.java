package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 工作流预览展开结果 VO — T16-4 强类型返回。
 *
 * <p>对齐 {@code WorkflowController（/api/v1/ecos/workflows）.previewWorkflow}
 * 既有 Map 输出：{@code workflowId / name / expandedSteps / estimatedPath / context}。
 * {@code expandedSteps} 为执行步迹、{@code context} 为调用方传入上下文，
 * 均为动态结构（T16-4: workflow 实例运行时 payload 动态结构豁免），
 * 保持 {@link Object} 类型；{@code estimatedPath} 实为 dry-run 执行状态字符串。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowPreviewVO {

    /** 工作流 ID */
    private String workflowId;

    /** 工作流名称 */
    private String name;

    /** 展开执行步迹（动态结构，T16-4: workflow 实例运行时 payload 动态结构豁免） */
    private Object expandedSteps;

    /** dry-run 执行状态（如 completed / failed） */
    private String estimatedPath;

    /** 调用方传入的预览上下文（动态结构，T16-4: workflow 实例运行时 payload 动态结构豁免） */
    private Object context;
}
