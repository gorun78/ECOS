package com.chinacreator.gzcm.engine.kb.dto;

import java.util.Map;

/** 按 job 回滚回执：{@code rolledBackNodes/rolledBackEdges/rolledBack}（删除行数）。 */
public record KgSyncRollbackVO(int rolledBackNodes, int rolledBackEdges, int rolledBack) {

    public static KgSyncRollbackVO of(Map<String, Object> source) {
        return new KgSyncRollbackVO(
                intOf(source.get("rolledBackNodes")),
                intOf(source.get("rolledBackEdges")),
                intOf(source.get("rolledBack")));
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
