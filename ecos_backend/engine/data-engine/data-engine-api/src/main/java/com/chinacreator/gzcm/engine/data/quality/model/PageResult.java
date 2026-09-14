package com.chinacreator.gzcm.engine.data.quality.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 分页结果 DTO — DQ 规则列表端点出参（通用，当前用于 {@link DqRuleVO}）。
 *
 * @param <T> 行类型
 * @author PMO-48-A T3
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> data;

    /** 符合条件的总行数 */
    private Long total;

    /** 页码（从 1 开始） */
    private Integer pageNum;

    /** 每页大小 */
    private Integer pageSize;

    /**
     * 构造分页结果。
     *
     * @param data     当前页数据
     * @param total    总行数
     * @param pageNum  页码
     * @param pageSize 每页大小
     */
    public PageResult(List<T> data, Long total, Integer pageNum, Integer pageSize) {
        this.data = data;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public PageResult() {
    }
}
