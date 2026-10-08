package com.chinacreator.gzcm.engine.cognitive2.service.calc;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * E2 确定性评分纯函数（详细设计-05 F05-04 / C102 / §五"新增纯函数"）。
 *
 * <p>禁依赖 Spring/DB（B-2 / 铁律 §0.2.1：数值确定由 Java 算术完成，LLM 零参与）。
 * {@code score = probability × trend_weight}；本期 {@code trend_weight} 恒 1（配置项
 * {@code ecos.cognitive.eval.trend_weight} 仅 Properties 入口）。Top-N 默认 10、上限 50 硬截断，
 * {@code limit<=0}→10，{@code limit>50}→截断 50 且 {@code truncated=true}（禁静默丢数据）。</p>
 */
public final class CognitiveEvalCalculator {

    /** 入项：一条待评分 belief 的分子（probability）。 */
    public record Input(String metricId, String name, double probability) {}

    /** 出项：Top-N 一行的评分 + 趋势标注。 */
    public record Scored(String metricId, String name, double score, String trend, String trendSource) {}

    /** 结果：Top-N 列表 + 有效 limit + 是否触发硬截断。 */
    public record Result(int limit, boolean truncated, List<Scored> items) {}

    private static final String TREND = "FLAT";
    private static final String TREND_SOURCE = "NOT_IMPLEMENTED";

    private CognitiveEvalCalculator() {}

    /** {@code score = probability × trend_weight}，4 位小数 HALF_UP。 */
    public static double score(double probability, double trendWeight) {
        BigDecimal v = BigDecimal.valueOf(probability).multiply(BigDecimal.valueOf(trendWeight));
        return v.setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    /** limit 归一：{@code <=0}→默认 10；{@code >50}→50。 */
    public static int bindLimit(Integer limitRaw) {
        if (limitRaw == null || limitRaw <= 0) return CognitiveApiPaths.E2_LIMIT_DEFAULT;
        return Math.min(limitRaw, CognitiveApiPaths.E2_LIMIT_MAX);
    }

    /**
     * 排序（score desc，name asc 兜底稳定）→ 取 Top-N → 标 truncated。
     * 本期 trend 恒 FLAT 且 trendSource=NOT_IMPLEMENTED（禁伪造趋势）。
     */
    public static Result topN(List<Input> inputs, double trendWeight, Integer limitRaw) {
        int limit = bindLimit(limitRaw);
        List<Scored> all = new ArrayList<>();
        if (inputs != null) {
            for (Input in : inputs) {
                double s = score(in.probability(), trendWeight);
                all.add(new Scored(in.metricId(), in.name(), s, TREND, TREND_SOURCE));
            }
        }
        all.sort(Comparator.comparingDouble((Scored s) -> s.score()).reversed()
                .thenComparing(s -> s.name() == null ? "" : s.name()));
        boolean truncated = all.size() > limit;
        int n = Math.min(limit, all.size());
        List<Scored> items = new ArrayList<>(all.subList(0, n));
        return new Result(limit, truncated, items);
    }
}
