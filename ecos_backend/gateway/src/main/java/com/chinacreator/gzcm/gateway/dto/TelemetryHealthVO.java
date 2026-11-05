package com.chinacreator.gzcm.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 遥测健康检查：{@code service/status/timestamp[/spans_stored][/token_records]/db}。
 *
 * <p>成功腿带库计数（{@code spans_stored}/{@code token_records}，原值透传）；探测异常腿
 * 两计数不出现、{@code db} 落 {@code "error: …"}（与旧 Map 同形）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"service", "status", "timestamp", "spans_stored", "token_records", "db"})
public record TelemetryHealthVO(
        String service,
        String status,
        String timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("spans_stored") Object spansStored,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("token_records") Object tokenRecords,
        String db) {
}
