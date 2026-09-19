package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 图谱构建请求 DTO — POST {@code /api/v1/knowledge/graph/build} 入参（可选）。
 *
 * <p>{@code async} 默认 true（异步全量构建）；
 * {@code mode} 支持 {@code FULL}（全量，忽略水位线）/ {@code INCREMENTAL}（按 {@code kb_extract_watermark} 续读）；
 * {@code dryRun} = true 时只统计不落库（方案 §5.3「图谱构建与 dry-run 预览」）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphBuildRequest {

    /** 是否异步（当前统一异步返回，字段保留兼容既有契约） */
    private boolean async = true;

    /** 抽取模式：FULL（默认）/ INCREMENTAL */
    private String mode = "FULL";

    /** 是否 dry-run 预览（true = 不写图谱，仅回填结构化报告） */
    private boolean dryRun = false;

    public boolean isAsync() { return async; }

    public void setAsync(boolean async) { this.async = async; }

    public String getMode() { return mode; }

    public void setMode(String mode) { this.mode = mode; }

    public boolean isDryRun() { return dryRun; }

    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
}
