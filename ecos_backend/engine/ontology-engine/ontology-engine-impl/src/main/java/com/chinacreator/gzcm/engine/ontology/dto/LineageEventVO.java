package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 血缘事件行（{@code GET /lineage/events}）：{@code eventId/query/format/nodes/edges/parseAt}。
 *
 * <p>{@code nodes/edges} 为解析器原样 JSON 数组（动态结构，不冻结），{@code parseAt} 无值时折空串
 * （与原 Map 同形）。</p>
 */
public record LineageEventVO(
        String eventId,
        String query,
        String format,
        List<Map<String, Object>> nodes,
        List<Map<String, Object>> edges,
        String parseAt) {

    /** JSON 数组串 → List（失败/空串 → 空数组，保持旧解析行为）。 */
    public static List<Map<String, Object>> parseArray(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
