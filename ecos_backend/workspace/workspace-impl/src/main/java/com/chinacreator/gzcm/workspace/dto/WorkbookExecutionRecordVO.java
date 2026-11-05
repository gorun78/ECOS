package com.chinacreator.gzcm.workspace.dto;

/**
 * Workbook 执行历史记录：{@code id/language/code/sessionId/timestamp/result}。
 * {@code result} 为运行引擎原样结果（SQL 行列 / Python/R 输出，结构随语言不同，不冻结）。
 */
public record WorkbookExecutionRecordVO(
        String id,
        String language,
        String code,
        String sessionId,
                String timestamp,
        Object result) {
}
