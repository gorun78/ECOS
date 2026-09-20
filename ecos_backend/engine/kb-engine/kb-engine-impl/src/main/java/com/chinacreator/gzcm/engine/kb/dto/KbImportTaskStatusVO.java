package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 异步抽取任务状态 VO（N2：GET /extract/structured/status/{taskId}）。
 *
 * <p>字段映射自 runtime-task 的 {@code TaskStatus}，按真实 getter 映射：
 * <ul>
 *   <li>{@code status} — TaskStatus.Status 枚举名（PENDING/RUNNING/SUCCEEDED/FAILED/...）</li>
 *   <li>{@code progress} — 进度百分比（0~100，null → 0）</li>
 *   <li>{@code statusMessage} — 当前进度消息</li>
 *   <li>{@code startTime} — 开始时间（epoch ms，null → null）</li>
 *   <li>{@code endTime} — 结束时间（epoch ms，null → null）</li>
 *   <li>{@code result} — 执行结果 JSON 字符串（TaskStatus.getResult()，报告 VO JSON）</li>
 *   <li>{@code errorMessage} — 失败时的错误描述</li>
 * </ul>
 *
 * @author ECOS KB Team
 */
@Data
public class KbImportTaskStatusVO {

    /** runtime-task 任务 ID */
    private String taskId;

    /** 任务状态：PENDING / PARSING / PARSED / RUNNING / SUCCEEDED / FAILED / CANCELLED / TIMEOUT */
    private String status;

    /** 进度百分比（0~100，null → 0） */
    private Integer progress;

    /** 当前进度消息 */
    private String statusMessage;

    /** 开始时间（epoch 毫秒，未开始为 null） */
    private Long startTime;

    /** 结束时间（epoch 毫秒，未完成为 null） */
    private Long endTime;

    /** 执行结果 JSON 字符串（抽取报告 EntityInstanceExtractionReportVO 序列化） */
    private String result;

    /** 失败时的错误描述（成功时为 null） */
    private String errorMessage;
}
