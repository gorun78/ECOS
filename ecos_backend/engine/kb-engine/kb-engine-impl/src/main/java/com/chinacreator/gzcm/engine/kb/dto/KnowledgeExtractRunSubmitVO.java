package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 知识抽取运行提交应答 — 详设-07 §7.3.2 第 12 行
 * （{@code POST /api/v1/knowledge/extract/runs} → 202 {@code {taskId,status:"SUBMITTED",traceId}}）。
 *
 * <p>{@code jobId} 为 additive 字段：与 {@code taskId} 同源生成（铁律 §1.6-1），
 * 落 {@code ecos_kb_extract_audit.job_id}，供 {@code GET /runs/{taskId}} 回读时对齐审计行。</p>
 */
@Data
public class KnowledgeExtractRunSubmitVO {

    /** runtime-task 任务 ID（{@code GET /runs/{taskId}} 与 N2 状态端点均以此为主键）。 */
    private String taskId;

    /** 业务抽取 jobId（{@code KBK1S-} 前缀）。 */
    private String jobId;

    /** 固定 {@code SUBMITTED}（202 已受理；执行结果经 {@code GET /runs/{taskId}} 轮询）。 */
    private String status;

    /** 请求级 traceId（信封 {@code traceId} 同值）。 */
    private String traceId;
}
