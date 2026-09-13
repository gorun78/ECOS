package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 图谱全量构建请求 DTO — POST {@code /api/v1/knowledge/graph/build} 入参（可选）。
 *
 * <p>{@code async} 默认 true（runtime-task 异步全量构建）；
 * 当前实现忽略同步等待，统一异步返回。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphBuildRequest {

    private boolean async = true;

    public boolean isAsync() { return async; }

    public void setAsync(boolean async) { this.async = async; }
}
