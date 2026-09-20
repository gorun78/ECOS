package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 定时抽取任务创建/更新响应 VO（T5 批次 POST 出参）。
 *
 * <p>字段 {@code { id, scheduleId, nextRunAt }} —— 与任务书要求一致。</p>
 */
@Data
public class ScheduledExtractCreatedVO {

    /** 主键 id */
    private Long id;

    /** runtime-task 调度 id */
    private String scheduleId;

    /** 下次运行时间 ISO 字符串（如 {@code 2026-09-21T08:00:00}） */
    private String nextRunAt;
}
