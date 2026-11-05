package com.chinacreator.gzcm.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Token 用量汇总：{@code range[/totals][/by_model][/by_operation][/error]}。
 *
 * <p>成功腿三段聚合（totals 汇总行、by_model/by_operation 分组行）为遥测存储原样结构，
 * 不冻结；异常腿只补 {@code error}（NON_NULL）。</p>
 */
@JsonPropertyOrder({"range", "totals", "by_model", "by_operation", "error"})
public record TelemetryTokenSummaryVO(
        String range,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object totals,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("by_model") Object byModel,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("by_operation") Object byOperation,
        @JsonInclude(JsonInclude.Include.NON_NULL) String error) {
}
