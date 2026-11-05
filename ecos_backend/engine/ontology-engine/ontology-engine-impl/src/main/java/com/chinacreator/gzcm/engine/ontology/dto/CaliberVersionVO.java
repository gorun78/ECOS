package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.sql.Timestamp;
import java.util.Map;

/**
 * 单条口径版本行（{@code POST /{code}/versions} 插入后读回回执）。
 *
 * <p>键面 = {@code CaliberDao#findVersionById} 的 10 个显式列，逐字冻结（含列序）。</p>
 */
@JsonPropertyOrder({"id", "caliber_id", "version_no", "formula", "additive",
        "period_granularity", "status", "approved_by", "approved_at", "git_ref"})
public record CaliberVersionVO(
        String id,
        @JsonProperty("caliber_id") String caliberId,
        @JsonProperty("version_no") String versionNo,
        String formula,
        Integer additive,
        @JsonProperty("period_granularity") String periodGranularity,
        String status,
        @JsonProperty("approved_by") String approvedBy,
        @JsonProperty("approved_at") Timestamp approvedAt,
        @JsonProperty("git_ref") String gitRef) {

    public static CaliberVersionVO of(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new CaliberVersionVO(
                asString(row.get("id")),
                asString(row.get("caliber_id")),
                asString(row.get("version_no")),
                asString(row.get("formula")),
                asInteger(row.get("additive")),
                asString(row.get("period_granularity")),
                asString(row.get("status")),
                asString(row.get("approved_by")),
                asTimestamp(row.get("approved_at")),
                asString(row.get("git_ref")));
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer asInteger(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static Timestamp asTimestamp(Object o) {
        return o instanceof Timestamp ts ? ts : null;
    }
}
