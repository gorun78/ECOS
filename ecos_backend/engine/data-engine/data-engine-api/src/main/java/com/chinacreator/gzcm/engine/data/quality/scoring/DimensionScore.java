package com.chinacreator.gzcm.engine.data.quality.scoring;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 单维度评估结果 DTO（PMO-48-B T8 SPI 出参）。
 *
 * <p>评估器不写库，仅返回 0.0-1.0 规范化分值 + 样本规模诊断 + 命中规则数 + 上下文 detail。
 * 落库（dq_score_snapshot + dq_score_asset）由 {@link com.chinacreator.gzcm.engine.data.quality.DqScoreService#recomputeForAsset}
 * 统一执行。</p>
 *
 * <p>detail 字段语义（对齐方案 §4.1 / 安全卡）：</p>
 * <ul>
 *   <li>{@code values} — 样本字段命中值（敏感字段值整体替换为 {@code ***}；铁律 2.4 #3）</li>
 *   <li>{@code threshold} — 维度阈值（如 freshness 的 5/60 分钟阈值）</li>
 *   <li>{@code relatedAlertId} — 触发的告警 ID（Phase 3 接入）</li>
 *   <li>{@code sampleStats} — 样本统计（合法值/失败值/缺失值计数）</li>
 * </ul>
 *
 * @author PMO-48-B T8
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DimensionScore {

    /** 维度编码（DqDimension name） */
    private DqDimension dimension;

    /** 该维度规范化分 (0.0 - 1.0) */
    private double scoreValue;

    /** 本次评估使用的样本量（totalRows/抽样上限 1000） */
    private int sampleSize;

    /** 命中的去重规则数 */
    private int ruleCount;

    /** 评估上下文详情（最终序列化为 JSONB 落 metadata） */
    private Map<String, Object> details;

    /** 执行时间（由 DqScoreService 兜底填充，供 snapshot 写 evaluated_at） */
    private Long evaluatedAt;

    public static DimensionScore timeout(DqDimension dim, int sampleSize, int ruleCount) {
        DimensionScore s = new DimensionScore();
        s.setDimension(dim);
        s.setScoreValue(0.0D);
        s.setSampleSize(sampleSize);
        s.setRuleCount(ruleCount);
        java.util.LinkedHashMap<String, Object> d = new java.util.LinkedHashMap<>();
        d.put("status", "TIMEOUT");
        s.setDetails(d);
        return s;
    }

    /** 时间戳辅助：返回当前 epoch ms（detail 缓存用）。 */
    public static long nowEpochMs() {
        return System.currentTimeMillis();
    }
}
