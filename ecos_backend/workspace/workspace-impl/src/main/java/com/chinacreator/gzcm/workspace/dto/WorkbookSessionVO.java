package com.chinacreator.gzcm.workspace.dto;

/** Workbook 会话摘要：{@code sessionId + executionCount}。 */
public record WorkbookSessionVO(String sessionId, int executionCount) {
}
