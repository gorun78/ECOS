package com.chinacreator.gzcm.workspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Workbook 运行环境状态：{@code available[/message]}（探测异常时补 message）。 */
public record WorkbookEnvStatusVO(
        boolean available,
        @JsonInclude(JsonInclude.Include.NON_NULL) String message) {
}
