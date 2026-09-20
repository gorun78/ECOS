package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.List;

/**
 * 定时抽取任务请求 DTO（T5 批次 create / update 共用入参）。
 *
 * <p>强类型 DTO（禁 {@code Map<String,Object>}）；字段非空校验由 Controller 层完成。
 */
@Data
public class ScheduledExtractRequest {

    /** 任务名称（非空） */
    private String name;

    /** 本体 ID 列表（可空 = 全本体） */
    private List<String> ontologyIds;

    /** 抽取模式：FULL / INCREMENTAL（默认 INCREMENTAL） */
    private String mode;

    /** 调度周期：DAILY / WEEKLY / MONTHLY */
    private String period;

    /** 触发时刻 "HH:mm"（如 "08:00"） */
    private String timeOfDay;

    /** 是否启用（update 场景使用；create 场景默认启用） */
    private Boolean enabled;
}
