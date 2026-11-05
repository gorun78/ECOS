package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.sql.Timestamp;
import java.util.Map;

/**
 * 当前生效口径版本行（{@code GET /api/v1/ontology/calibers/{code}/current} 跨引擎只读面）。
 *
 * <p>键面 = {@code CaliberDao#getByCaliberCodeAndCurrentVersion} 的 18 个显式别名列，
 * <b>逐字冻结</b>（含 {@code null} 值也照常出现，不复刻 NON_NULL 省略）。显式
 * {@link JsonPropertyOrder} 保持列序（Jackson 默认会把带 {@code @JsonProperty} 的属性
 * 后置，与旧 Map 的列序不同）。</p>
 */
@JsonPropertyOrder({"caliber_id", "code", "name", "caliber_status", "unit", "currency",
        "owner_role", "dimension_json", "domain", "version_id", "version_no", "formula",
        "additive", "period_granularity", "version_status", "approved_by", "approved_at",
        "git_ref"})
public record CaliberCurrentVO(
        @JsonProperty("caliber_id") String caliberId,
        String code,
        String name,
        @JsonProperty("caliber_status") String caliberStatus,
        String unit,
        String currency,
        @JsonProperty("owner_role") String ownerRole,
        @JsonProperty("dimension_json") String dimensionJson,
        String domain,
        @JsonProperty("version_id") String versionId,
        @JsonProperty("version_no") String versionNo,
        String formula,
        Integer additive,
        @JsonProperty("period_granularity") String periodGranularity,
        @JsonProperty("version_status") String versionStatus,
        @JsonProperty("approved_by") String approvedBy,
        @JsonProperty("approved_at") Timestamp approvedAt,
        @JsonProperty("git_ref") String gitRef) {

    public static CaliberCurrentVO of(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new CaliberCurrentVO(
                asString(row.get("caliber_id")),
                asString(row.get("code")),
                asString(row.get("name")),
                asString(row.get("caliber_status")),
                asString(row.get("unit")),
                asString(row.get("currency")),
                asString(row.get("owner_role")),
                asString(row.get("dimension_json")),
                asString(row.get("domain")),
                asString(row.get("version_id")),
                asString(row.get("version_no")),
                asString(row.get("formula")),
                asInteger(row.get("additive")),
                asString(row.get("period_granularity")),
                asString(row.get("version_status")),
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
