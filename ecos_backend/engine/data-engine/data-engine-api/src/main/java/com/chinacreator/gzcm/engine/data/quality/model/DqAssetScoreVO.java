package com.chinacreator.gzcm.engine.data.quality.model;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 资产级评分 VO — 对齐 {@code ecos_dq.dq_score_asset} 主表字段（PMO-48-B T8）。
 *
 * <p>聚合 6 维度评分 + 加权汇总 + 等级。落表 SQL：</p>
 * <pre>
 *   INSERT INTO ecos_dq.dq_score_asset (asset_type, asset_id, overall_score,
 *       rolled_up_scores, last_evaluated_at, is_deleted)
 *   VALUES (?, ?, ?, ?, NOW(), FALSE)
 *   ON CONFLICT (asset_type, asset_id) DO UPDATE
 *     SET overall_score = EXCLUDED.overall_score,
 *         rolled_up_scores = EXCLUDED.rolled_up_scores,
 *         last_evaluated_at = EXCLUDED.last_evaluated_at,
 *         is_deleted = FALSE;
 * </pre>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code overallScore} 0.0-1.0 — 6 维度按权重加权：{@code Σ(维度分×weight)/Σ(weight)}</li>
 *   <li>{@code grade} A/B/C/D/F — DqDimension#gradeOf 判定（A≥0.95/B≥0.85/C≥0.70/D≥0.50/F&lt;0.50）</li>
 *   <li>{@code dimensionScores} 6 维分数（key=DqDimension name, value=0.0-1.0）</li>
 * </ul>
 *
 * @author PMO-48-B T8
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqAssetScoreVO {

    /** 资产类型: DATASOURCE / TABLE / FIELD / DOMAIN / SYSTEM */
    private String assetType;

    /** 资产 ID */
    private String assetId;

    /** 加权汇总分 0.0-1.0 */
    private double rolledScore;

    /** 等级 A/B/C/D/F（DqDimension#gradeOf 已包含） */
    private String grade;

    /** 6 维分数（DqDimension name → 0.0-1.0） */
    private Map<String, Double> dimensionScores;

    /** 6 维权重累加（诊断用：Σ(weight)） */
    private double weightSum;

    /** 最后一次评估时间 */
    private LocalDateTime lastEvaluatedAt;

    /** 样本大小（最近一次评估的 totalRows 汇总） */
    private Integer sampleSize;

    /** 命中规则countsum (诊断位) */
    private Integer distinctRuleCount;

    /** 构造便捷：全部 6 维设为 0 + 默认 weight。 */
    public static DqAssetScoreVO blank(String assetType, String assetId) {
        DqAssetScoreVO vo = new DqAssetScoreVO();
        vo.setAssetType(assetType);
        vo.setAssetId(assetId);
        vo.setRolledScore(0.0D);
        vo.setGrade("F");
        vo.setDimensionScores(new LinkedHashMap<>());
        vo.setWeightSum(0.0D);
        return vo;
    }

    /** 维度 → 权重累加 (反查用)。 */
    public static double defaultWeight(DqDimension d) {
        return d == null ? 0.0D : d.getDefaultWeight();
    }
}
