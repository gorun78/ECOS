package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 图形态同步（投影）触发请求 — 详设-07 §7.2.3 ＋ 附录 A 第 15 条
 * （{@code POST /api/v1/knowledge/graph/sync}，入参 {@code {scope,fullResync?}}）。
 *
 * <p><b>scope 承载现状（如实声明）</b>：投影恒为 PG→图库全量镜像（PG 是唯一权威、图库是可重放
 * 的从属副本），{@code scope} 只作运维标签与台账/响应回显，<b>不</b>退化为按 scope 的部分镜像
 * ——即声明的范围与实际写入面不同形时，宁可如实回显，也不做「假过滤」。</p>
 */
@Data
public class GraphSyncRequest {

    /** 范围标签（缺省 {@code ALL}；仅作回显/台账，不改变全量镜像语义）。 */
    private String scope;

    /** 是否先清空投影子图再全量重写（脏投影的重放手段，缺省 {@code false} = 增量 MERGE）。 */
    private Boolean fullResync;
}
