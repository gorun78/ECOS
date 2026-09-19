package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 生命周期审计条目 VO — {@code GET /api/v1/knowledge/lifecycle/audit} 出参。
 *
 * <p>字段名与前端 {@code LifecycleAuditEntry}（typesAndConstants.ts）对齐：
 * {@code id / assetId / from / to / operator / at}；{@code action} 为附加说明（前端忽略）。</p>
 *
 * <p><b>数据口径</b>：仓库尚无专表记录资产状态变更，故按任务裁决从既有作业台账
 * {@code ecos_knowledge.kg_sync_log} 投影——每条作业记录视为「图谱资产」的一次生命周期事件，
 * {@code from=draft}，{@code to} 由作业结果映射（SUCCESS→active / FAILED→deprecated / 其他→draft）。
 * 该投影忠实反映真实作业记录（时间/范围/结果），不代表人工状态机流转。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LifecycleAuditEntryVO {

    /** 审计条目 ID（源于台账主键，形如 sync-3） */
    private String id;

    /** 关联资产 ID（取台账 object_type，如 ALL / 本体 ID） */
    private String assetId;

    /** 变更前状态 */
    private String from;

    /** 变更后状态 */
    private String to;

    /** 操作者 */
    private String operator;

    /** 发生时刻（ISO-8601） */
    private String at;

    /** 原始作业操作类型（附加说明，前端忽略） */
    private String action;
}
