package com.chinacreator.gzcm.workspace.analysis;

import java.util.Map;

/**
 * 分析发起请求体（强类型出口，2026-11-21 场景工作台简化 v0.3）。
 *
 * <p>取代 Controller 上的 {@code Map<String,Object>}：弱类型出入参会撞
 * {@code ControllerMapPayloadRatchetTest}（只减不增）。字段与 §4.1 运行发起器要素对齐；
 * 预测五要素在此声明，缺项由服务层判 400（不静默降级）。</p>
 */
public record AnalysisRunRequest(
        String analysisType,
        String runMode,
        String metric,
        Double deviation,
        String caliberId,
        String caliberVersion,
        String asOfTime,
        String snapshotId,
        String formulaVersion) {

    /** 转服务层接收的参数字典（服务内部仍以 Map 承载，DTO 只在边界）。 */
    public Map<String, Object> toParams() {
        Map<String, Object> p = new java.util.LinkedHashMap<>();
        put(p, "analysisType", analysisType);
        put(p, "runMode", runMode);
        put(p, "metric", metric);
        if (deviation != null) p.put("deviation", deviation);
        put(p, "caliberId", caliberId);
        put(p, "caliberVersion", caliberVersion);
        put(p, "asOfTime", asOfTime);
        put(p, "snapshotId", snapshotId);
        put(p, "formulaVersion", formulaVersion);
        return p;
    }

    private static void put(Map<String, Object> p, String key, String value) {
        if (value != null && !value.isBlank()) p.put(key, value);
    }
}
