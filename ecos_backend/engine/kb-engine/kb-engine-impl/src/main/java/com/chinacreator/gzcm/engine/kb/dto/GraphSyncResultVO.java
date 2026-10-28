package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 图形态同步（投影）触发结果 — 附录 A 第 15 条
 * （{@code POST /api/v1/knowledge/graph/sync} 出参 {@code {attempted,written,notAttempted,reason,reconcileDiff}}）。
 *
 * <p>计数单位一律为<b>节点数</b>；边经 {@code reconcileDiff} 呈现（含 {@code edgeSkipped}）。</p>
 *
 * <p><b>诚实口径</b>：{@code graphForm=PG_ONLY} 时 {@code attempted=0}、{@code written=0}、
 * {@code notAttempted}=PG 节点数、{@code reason} 必非空——投影「未尝试」而不是「失败」；
 * 一旦真写则对账必须一致，否则端点以 500 {@code ECOS-KB-080} 上浮（不会走到本出参）。</p>
 */
@Data
public class GraphSyncResultVO {

    /** 形态：{@code PROJECTED}（已投影且对账一致）/ {@code PG_ONLY}（本档位无投影能力）。 */
    private String graphForm;

    /** 尝试投影的节点数（PG 权威侧装载数）。 */
    private Integer attempted;

    /** 实际写入并可读回的图库节点数。 */
    private Integer written;

    /** 未尝试投影的节点数（{@code PG_ONLY} 时 = PG 节点数）。 */
    private Integer notAttempted;

    /** 未尝试原因（{@code PG_ONLY} 必填；{@code PROJECTED} 为 {@code null}）。 */
    private String reason;

    /** 对账明细。 */
    private GraphReconcileDiffVO reconcileDiff;

    /** 本次投影台账任务 ID（{@code ecos_kg_sync_log.job_id}）。 */
    private String jobId;

    /** 范围标签回显（缺省 {@code ALL}）。 */
    private String scope;

    /** 全链追踪 ID（V179 {@code trace_id}，与台账行同值）。 */
    private String traceId;
}
