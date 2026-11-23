package com.chinacreator.gzcm.workspace.analysis;

import java.util.Map;

/**
 * 分析对比结果视图（强类型出口，2026-11-21 场景工作台简化 v0.3）。
 *
 * <p>只声明对比卡片必需的四段；其余差异明细暂留 Map（服务层仍在演化），
 * 边界类型化是棘轮门的硬要求，明细不进类型面。</p>
 */
public record AnalysisCompareVO(
        Map<String, Object> baseline,
        Map<String, Object> candidate,
        Map<String, Object> delta,
        String verdict) {

    @SuppressWarnings("unchecked")
    public static AnalysisCompareVO of(Map<String, Object> m) {
        return new AnalysisCompareVO(
                (Map<String, Object>) m.getOrDefault("baseline", Map.of()),
                (Map<String, Object>) m.getOrDefault("candidate", Map.of()),
                (Map<String, Object>) m.getOrDefault("delta", Map.of()),
                m.get("verdict") == null ? null : String.valueOf(m.get("verdict")));
    }
}
