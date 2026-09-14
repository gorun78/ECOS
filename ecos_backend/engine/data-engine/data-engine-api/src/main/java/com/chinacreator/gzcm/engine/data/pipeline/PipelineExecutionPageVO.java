package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.util.List;

/**
 * Pipeline 执行历史分页结果 — GET /api/v1/pipeline/definitions/{id}/executions 响应载荷。
 * <p>遵循后端规范分页接口 pageNum/pageSize 约定（Lombok @Data）。
 *
 * @author DataBridge Datanet Team
 */
@Data
public class PipelineExecutionPageVO {

    /** 当前页码（从 1 开始） */
    private int page;

    /** 每页大小 */
    private int pageSize;

    /** 执行记录总数 */
    private long total;

    /** 当前页执行记录 */
    private List<PipelineExecutionVO> items;

    /**
     * 构造一个空分页结果（无记录时）。
     */
    public static PipelineExecutionPageVO empty(int page, int pageSize) {
        PipelineExecutionPageVO vo = new PipelineExecutionPageVO();
        vo.setPage(page);
        vo.setPageSize(pageSize);
        vo.setTotal(0L);
        vo.setItems(java.util.Collections.emptyList());
        return vo;
    }
}
