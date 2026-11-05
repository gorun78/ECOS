package com.chinacreator.gzcm.workspace.dto;

/**
 * Workbook 健康检查：{@code sql/python/r} 三段。
 * {@code r} 为 R 运行环境原样信息（runtime 侧结构，不冻结）。
 */
public record WorkbookHealthVO(WorkbookEnvStatusVO sql, WorkbookEnvStatusVO python, Object r) {
}
