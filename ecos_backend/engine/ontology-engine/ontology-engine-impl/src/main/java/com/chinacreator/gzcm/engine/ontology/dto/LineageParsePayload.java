package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析结果 → 持久化/入边辅助（解析器输出为动态 JSON，不冻结）。
 */
public final class LineageParsePayload {

    private LineageParsePayload() {
    }

    public static List<Map<String, Object>> nodes(LineageParseResponse resp) {
        return resp != null && resp.getNodes() != null ? resp.getNodes() : new ArrayList<>();
    }

    public static List<Map<String, Object>> edges(LineageParseResponse resp) {
        return resp != null && resp.getEdges() != null ? resp.getEdges() : new ArrayList<>();
    }

    /** 列表 → JSON 串（失败回落 "[]"，与旧实现一致）。 */
    public static String toJson(List<Map<String, Object>> list) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
