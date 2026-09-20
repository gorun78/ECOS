package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 抽取审计日志条目（T6 端点 {@code GET /api/v1/knowledge/extract/structured/logs/{jobId}}
 * 出参元素）— 从 {@code kb_extract_audit} 行映射：
 * <ul>
 *   <li>{@code ts} — audit 行 {@code created_at} 转 ISO 字符串</li>
 *   <li>{@code level} — 按 status 推断：SUCCEEDED / RUNNING → INFO；FAILED / 其他 → ERROR</li>
 *   <li>{@code message} — 一行描述，含 {@code job / status / durationMs / rows / error}</li>
 * </ul>
 */
@Data
public class ExtractLogEntry {

    /** 时间戳（ISO-8601，如 {@code 2026-09-20T08:00:00}） */
    private String ts;

    /** 日志级别：INFO / WARN / ERROR（按 status 推断） */
    private String level;

    /** 日志内容 */
    private String message;
}
