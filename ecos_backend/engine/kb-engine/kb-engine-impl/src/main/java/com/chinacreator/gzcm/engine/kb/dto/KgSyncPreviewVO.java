package com.chinacreator.gzcm.engine.kb.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * dry-run 预览：{@code create/update/skip/ontologyId/ontologyVersions/entityCount/relationshipCount}。
 * {@code create/update/skip} 按 Q4 裁决恒 0（不物化本体实体为图节点）。
 */
public record KgSyncPreviewVO(
        int create,
        int update,
        int skip,
        String ontologyId,
        List<String> ontologyVersions,
        int entityCount,
        int relationshipCount) {

    public static KgSyncPreviewVO of(Map<String, Object> source) {
        List<String> versions = new ArrayList<>();
        if (source.get("ontologyVersions") instanceof List<?> list) {
            for (Object v : list) {
                versions.add(v == null ? null : String.valueOf(v));
            }
        }
        return new KgSyncPreviewVO(
                intOf(source.get("create")),
                intOf(source.get("update")),
                intOf(source.get("skip")),
                str(source.get("ontologyId")),
                versions,
                intOf(source.get("entityCount")),
                intOf(source.get("relationshipCount")));
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
