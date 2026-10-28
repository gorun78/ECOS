package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 同步日志条目 — 附录 A 第 15 条（{@code GET /api/v1/knowledge/graph/sync/logs}，条目须含 {@code traceId}）。
 *
 * <p>来源：{@code ecos_knowledge.ecos_kg_sync_log}（V179）；{@code status} 已归一为前端词表
 * （{@code pending/running/success/failed}），{@code timestamp/finishedAt} 为库内原值字符串。</p>
 */
@Data
public class GraphSyncLogEntryVO {

    /** 台账任务 ID。 */
    private String jobId;

    /** 台账 {@code object_type}（投影腿为 scope 标签，截列宽 20）。 */
    private String objectType;

    /** 操作类型（{@code NEO4J_SYNC} / {@code FULL_SYNC} / {@code INCREMENTAL_SYNC} 等）。 */
    private String operation;

    /** 归一状态：{@code pending/running/success/failed}。 */
    private String status;

    /** 进度（0~100）。 */
    private Integer progress;

    /** 节点数。 */
    private Integer nodes;

    /** 边数。 */
    private Integer edges;

    /** 失败摘要（成功行为 {@code null} / 空串）。 */
    private String message;

    /** 创建时刻（库内原值）。 */
    private String timestamp;

    /** 完成时刻（未完成 {@code null}）。 */
    private String finishedAt;

    /** 全链追踪 ID（V179 {@code trace_id}；历史行可为 {@code null}）。 */
    private String traceId;
}
