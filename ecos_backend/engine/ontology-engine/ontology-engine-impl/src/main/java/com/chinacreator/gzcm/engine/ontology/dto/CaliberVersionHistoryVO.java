package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.sql.Timestamp;
import java.util.Map;

/**
 * 口径版本历史行（{@code GET /{code}} 详情的 {@code versions[]}）。
 *
 * <p>键面 = {@code CaliberDao#listVersionsByCaliberId} 的 16 个显式列，逐字冻结（含列序）。</p>
 */
@JsonPropertyOrder({"id", "caliber_id", "version_no", "formula", "additive",
        "period_granularity", "status", "approved_by", "approved_at", "git_ref",
        "create_time", "update_time", "create_by", "update_by", "is_deleted", "domain"})
public record CaliberVersionHistoryVO(
        String id,
        @JsonProperty("caliber_id") String caliberId,
        @JsonProperty("version_no") String versionNo,
        String formula,
        Integer additive,
        @JsonProperty("period_granularity") String periodGranularity,
        String status,
        @JsonProperty("approved_by") String approvedBy,
        @JsonProperty("approved_at") Timestamp approvedAt,
        @JsonProperty("git_ref") String gitRef,
        @JsonProperty("create_time") Timestamp createTime,
        @JsonProperty("update_time") Timestamp updateTime,
        @JsonProperty("create_by") String createBy,
        @JsonProperty("update_by") String updateBy,
        @JsonProperty("is_deleted") Integer isDeleted,
        String domain) {

    public static CaliberVersionHistoryVO of(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new CaliberVersionHistoryVO(
                asString(row.get("id")),
                asString(row.get("caliber_id")),
                asString(row.get("version_no")),
                asString(row.get("formula")),
                asInteger(row.get("additive")),
                asString(row.get("period_granularity")),
                asString(row.get("status")),
                asString(row.get("approved_by")),
                asTimestamp(row.get("approved_at")),
                asString(row.get("git_ref")),
                asTimestamp(row.get("create_time")),
                asTimestamp(row.get("update_time")),
                asString(row.get("create_by")),
                asString(row.get("update_by")),
                asInteger(row.get("is_deleted")),
                asString(row.get("domain")));
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
