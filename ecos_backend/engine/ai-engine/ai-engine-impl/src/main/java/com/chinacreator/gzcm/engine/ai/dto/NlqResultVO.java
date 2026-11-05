package com.chinacreator.gzcm.engine.ai.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * NLQ 查询回执（{@code POST /api/v1/query/nlq}）。
 *
 * <p>键序：{@code translated/results[/agent_answer][/message]}。{@code results} 为查询结果行
 * （列名统一转小写，列集随 ObjectQL 动态，不冻结）；{@code agent_answer}/{@code message}
 * 仅在 Agent Runtime 回退腿出现（条件键 NON_NULL）。</p>
 */
@JsonPropertyOrder({"translated", "results", "agent_answer", "message"})
public record NlqResultVO(
        String translated,
        List<Map<String, Object>> results,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @com.fasterxml.jackson.annotation.JsonProperty("agent_answer") String agentAnswer,
        @JsonInclude(JsonInclude.Include.NON_NULL) String message) {

    /** 字段名统一转小写（DB 列名大小写不敏感，语义与原实现一致）。 */
    public static List<Map<String, Object>> lowercaseRows(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                m.put(entry.getKey() != null ? entry.getKey().toLowerCase() : null, entry.getValue());
            }
            return m;
        }).collect(Collectors.toList());
    }

    public static NlqResultVO of(String translatedJson, List<Map<String, Object>> rows) {
        return new NlqResultVO(translatedJson, lowercaseRows(rows), null, null);
    }
}
