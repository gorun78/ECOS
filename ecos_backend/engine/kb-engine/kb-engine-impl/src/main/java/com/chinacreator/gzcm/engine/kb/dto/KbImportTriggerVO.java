package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 异步抽取触发响应 VO（N1：POST /extract/structured dryRun=false）。
 *
 * <p>异步模式下，Controller 先生成 jobId 放入 TaskDescription.parameters，
 * 随即通过 {@code ITaskManagementService.submitTask} + {@code executeTask}
 * 提交任务到 runtime-task；本 VO 携带 {@code taskId}（runtime-task 侧）与
 * {@code jobId}（业务侧，用于 E4/E5 溯源），供前端轮询 N2 状态端点。
 *
 * @author ECOS KB Team
 */
@Data
public class KbImportTriggerVO {

    /** runtime-task 任务 ID（用于 N2 轮询 {@code GET /status/{taskId}}） */
    private String taskId;

    /** 业务抽取 jobId（{@code KBK1S-} 前缀，对应 extract 报告与 kb_extract_audit） */
    private String jobId;
}
