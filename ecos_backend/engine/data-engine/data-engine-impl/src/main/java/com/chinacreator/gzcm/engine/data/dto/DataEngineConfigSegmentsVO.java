package com.chinacreator.gzcm.engine.data.dto;

import java.util.List;
import java.util.Map;

/**
 * 数据湖分层分段配置视图（{@code GET /api/v1/engine/data/settings/groups}）。
 *
 * <p>键面：{@code segments[].id} + {@code segments[].subgroups[].id/configs}；
 * 段与子分区顺序由控制器固定表决定，{@code configs} 以完整 {@code config_key} 为键。</p>
 */
public record DataEngineConfigSegmentsVO(List<Segment> segments) {

    public record Segment(String id, List<Subgroup> subgroups) {
    }

    public record Subgroup(String id, Map<String, String> configs) {
    }
}
