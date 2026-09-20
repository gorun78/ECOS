package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.List;

/**
 * 定时抽取任务列表项 VO（T5 批次 GET / 出参）。
 */
@Data
public class ScheduledExtractVO {

    /** 主键 */
    private Long id;

    /** runtime-task scheduleId */
    private String scheduleId;

    /** 任务名称 */
    private String name;

    /** 本体 ID 列表 */
    private List<String> ontologyIds;

    /** 抽取模式：FULL / INCREMENTAL */
    private String mode;

    /** 调度周期：DAILY / WEEKLY / MONTHLY */
    private String period;

    /** 触发时刻 "HH:mm"（从 cron 表达式第 2 位推导） */
    private String timeOfDay;

    /** 是否启用 */
    private Boolean enabled;

    /** 上次运行时间 ISO 字符串（未运行转为 null） */
    private String lastRunAt;

    /** 上次运行状态（SUCCEEDED / FAILED 等） */
    private String lastStatus;

    /** 创建时间 ISO 字符串 */
    private String createdAt;
}
