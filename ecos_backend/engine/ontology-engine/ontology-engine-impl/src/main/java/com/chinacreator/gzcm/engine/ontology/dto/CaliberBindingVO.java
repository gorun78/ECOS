package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

/**
 * 指标口径绑定回执（{@code POST /metrics/{metricCode}/binding}）。
 *
 * <p>键面 = {@code CaliberService#bindMetricCaliber} 返回的 4 键，逐字冻结（含键序）。</p>
 */
@JsonPropertyOrder({"metricCode", "caliber_id", "formula_version", "caliber_snapshot"})
public record CaliberBindingVO(
        String metricCode,
        @JsonProperty("caliber_id") String caliberId,
        @JsonProperty("formula_version") String formulaVersion,
        @JsonProperty("caliber_snapshot") String caliberSnapshot) {

    public static CaliberBindingVO of(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new CaliberBindingVO(
                asString(row.get("metricCode")),
                asString(row.get("caliber_id")),
                asString(row.get("formula_version")),
                asString(row.get("caliber_snapshot")));
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
