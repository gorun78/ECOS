package com.chinacreator.gzcm.engine.ai.dto;

/**
 * Pipeline 列表摘要：{@code id/name/description/status/createdAt/updatedAt}（不含 steps）。
 */
public record AipPipelineSummaryVO(
        String id,
        String name,
        String description,
        String status,
        String createdAt,
        String updatedAt) {
}
