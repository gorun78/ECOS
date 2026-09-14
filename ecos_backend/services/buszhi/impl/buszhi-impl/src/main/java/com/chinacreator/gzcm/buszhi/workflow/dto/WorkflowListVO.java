package com.chinacreator.gzcm.buszhi.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 工作流定义列表包装 VO — 强类型返回容器。
 * （溯源：ontology-engine-impl T16-4 等价移植，PMO-57 T2 迁入 buszhi 服务层。）
 *
 * <p>对齐 {@code WorkflowController listWorkflows}
 * 既有 Map 输出结构：{@code data}（按 pageSize 截断的列表） + {@code total}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowListVO {

    /** 工作流列表（pageSize 截断） */
    private List<WorkflowVO> data;

    /** 总记录数（service.totalCount()） */
    private Long total;

    public WorkflowListVO() {
    }

    public List<WorkflowVO> getData() {
        return data;
    }

    public void setData(List<WorkflowVO> data) {
        this.data = data;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }
}
