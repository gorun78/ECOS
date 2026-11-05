package com.chinacreator.gzcm.sysman.controller.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 因果图边：DB 路径 5 键（含 {@code description}）、内存兜底 4 键（无 {@code description}）。
 *
 * <p>{@code description} 用 NON_NULL 省略，保持内存兜底路径的 4 键 wire 不变。</p>
 */
public record WorldModelCausalEdgeVO(
        String id,
        String source,
        String target,
        String label,
        @JsonInclude(JsonInclude.Include.NON_NULL) String description) {

    public static WorldModelCausalEdgeVO ofRow(Map<String, Object> row) {
        return new WorldModelCausalEdgeVO(
                asString(row.get("id")),
                asString(row.get("source")),
                asString(row.get("target")),
                asString(row.get("label")),
                asString(row.get("description")));
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
