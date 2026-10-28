package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 图形态只读状态 — 附录 A 第 15 条
 * （{@code GET /api/v1/knowledge/graph/sync/status} 出参 {@code {graphForm,lastSyncTime,diff}}）。
 *
 * <p>{@code graphForm} 三态互不折叠：{@code PG_ONLY}（无投影 / 投影读数不可得）、
 * {@code PROJECTED}（对账一致）、{@code MISMATCH}（对账漂移，另带 {@code mismatchCode}）。
 * 漂移是被读的对象，故读面仍 200；台账/PG 读故障则上浮 500 {@code ECOS-KB-050}。</p>
 */
@Data
public class GraphSyncStatusVO {

    /** 形态：{@code PG_ONLY} / {@code PROJECTED} / {@code MISMATCH}。 */
    private String graphForm;

    /** 最近一次成功同步完成时刻（ISO-8601 带时区；无成功行为 {@code null}）。 */
    private String lastSyncTime;

    /** 对账明细（{@code matched=null} = 投影端读数不可得）。 */
    private GraphReconcileDiffVO diff;

    /** 漂移机读码（仅 {@code MISMATCH} 时给，= {@code ECOS-KB-080}）。 */
    private String mismatchCode;

    /** 形态说明（{@code PG_ONLY} 的原因 / {@code MISMATCH} 的差异摘要）。 */
    private String reason;
}
