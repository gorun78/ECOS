package com.chinacreator.gzcm.engine.data.dto;

/**
 * 批量更新回执（{@code PUT /api/v1/engine/data/settings}）：{@code updated} 为实际写入行数，
 * {@code total} 为提交条数。
 */
public record DataEngineConfigUpdateVO(int updated, int total) {
}
