package com.chinacreator.gzcm.sysman.controller.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 因果图节点：DB 路径 5 键（含 {@code type}）、内存兜底 4 键（无 {@code type}）。
 *
 * <p>{@code type} 用 NON_NULL 省略，保持内存兜底路径的 4 键 wire 不变。</p>
 */
public record WorldModelCausalNodeVO(
        String id,
        String label,
        String description,
        String status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String type) {

    public static WorldModelCausalNodeVO ofRow(Map<String, Object> row) {
        return new WorldModelCausalNodeVO(
                asString(row.get("id")),
                asString(row.get("label")),
                asString(row.get("description")),
                asString(row.get("status")),
                asString(row.get("type")));
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
