package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 工作流定义列表包装 VO — T16-4 强类型返回容器。
 *
 * <p>对齐 {@code WorkflowController（/api/v1/ecos/workflows）.listWorkflows}
 * 既有 Map 输出结构：{@code data}（按 pageSize 截断的列表） + {@code total}。
 * 与 T16-2 {@link OntologyWorkflowPageVO}（engine 路径 records+data 分页结构）
 * 输出键不同，故独立定义，不复用。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowListVO {

    /** 工作流列表（pageSize 截断） */
    private List<OntologyWorkflowVO> data;

    /** 总记录数（service.totalCount()） */
    private Long total;
}
