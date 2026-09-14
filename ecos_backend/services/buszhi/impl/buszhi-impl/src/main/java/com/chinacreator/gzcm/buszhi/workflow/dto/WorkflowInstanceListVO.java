package com.chinacreator.gzcm.buszhi.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 工作流实例列表包装 VO — 强类型返回容器。
 * （溯源：ontology-engine-impl T16-4 等价移植，PMO-57 T2 迁入 buszhi 服务层。）
 *
 * <p>对齐 {@code /api/v1/ecos/workflows/instances} 既有 API 输出结构：
 * {@code data}（实例列表） + {@code total}（条数）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowInstanceListVO {

    /** 实例列表 */
    private List<WorkflowInstanceVO> data;

    /** 实例条数（= data.size()，兼容既有消费者） */
    private Integer total;

    public WorkflowInstanceListVO() {
    }

    public List<WorkflowInstanceVO> getData() {
        return data;
    }

    public void setData(List<WorkflowInstanceVO> data) {
        this.data = data;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }
}
