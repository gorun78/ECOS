package com.chinacreator.gzcm.engine.cognitive2.dto.counterfactual;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 反事实推演请求 DTO（PMO-59 P3a / ADR-9 心智层推演核心）。
 *
 * <p>语义：do(A) 干预式反事实推演——对指定业务域的一组不可观测经营变量
 * （beliefs 当前版本分布），施加一组干预（SET 定点 / DELTA 平移）后
 * 蒙特卡洛 N 次采样，输出风险四指标 + 敏感性 Top3。
 * <b>纯 Java 数值计算，LLM 零参与（ADR-5 口径）。</b>
 *
 * <p>数值映射约定（指令 §实现决策 1）：
 * <ul>
 *   <li>分布 outcome → 数值：优先取 {@code baseline.outcomeValues[variableName][outcome]}；
 *       缺省的 outcome 按序数 i+1（第 0 个 outcome=1.0）映射；</li>
 *   <li>线性响应：{@code metric_t = Σ_i weights[i] · x_{i,t}}，weights 缺省 1.0；</li>
 *   <li>干预：SET → 该变量每次采点后强制覆写为 value；DELTA → 该变量每个 outcome 数值 +value。</li>
 * </ul>
 *
 * <p>采样上限 {@code MAX_SAMPLE_COUNT=5000}；{@code seed} 缺省 42（同参数同 seed 双跑逐位一致，
 * 保证可复现；异 seed 数值可差异但分布形态一致）。</p>
 */
@Data
public class CounterfactualRequest {

    /** sampleCount 下限（防 0 采样除零）。 */
    public static final int MIN_SAMPLE_COUNT = 10;

    /** sampleCount 上限（同步端点压测预算内，指令 T4 验收口径）。 */
    public static final int MAX_SAMPLE_COUNT = 5000;

    /** seed 缺省值（可复现基线）。 */
    public static final long DEFAULT_SEED = 42L;

    /** 场景 id（可选，仅作结果回显与关联 ecos_scenario_run 的语义键）。 */
    private String scenarioId;

    /** 业务域（必填，belief 分布装配域）。 */
    private String domain;

    /** 被推演的主变量名（必填，结果回显；线性响应指标以此为口径命名）。 */
    private String variableName;

    /** 基线参数（outcome 数值映射 + 线性响应权重；缺省 outcome 按序数映射、权重 1.0）。 */
    private Baseline baseline;

    /** 干预变量列表（do(A) 语义；空/缺省 = 纯基线蒙特卡洛，仍返回四指标（相对自身为 0））。 */
    private List<Intervention> interventions = new ArrayList<>();

    /** 蒙特卡洛采样次数（默认 1000，上限 5000）。 */
    private Integer sampleCount;

    /** 采样随机种子（缺省 42；保证同参数可复现）。 */
    private Long seed;

    /** 有效采样次数（DTO 校验后回填语义：缺省→1000，超限截断到 5000，下限 10）。 */
    public int resolvedSampleCount() {
        if (sampleCount == null) {
            return 1000;
        }
        return Math.max(MIN_SAMPLE_COUNT, Math.min(MAX_SAMPLE_COUNT, sampleCount));
    }

    /** 有效种子（缺省 → DEFAULT_SEED）。 */
    public long resolvedSeed() {
        return seed == null ? DEFAULT_SEED : seed;
    }

    /** 基线参数：outcome→数值映射 + 变量→线性响应权重。 */
    @Data
    public static class Baseline {
        /** outcome 数值映射：{variableName: {outcome: value}}（未列出的 outcome 按序数 i+1）。 */
        private Map<String, Map<String, Double>> outcomeValues = new LinkedHashMap<>();
        /** 线性响应权重：{variableName: weight}（缺省 1.0）。 */
        private Map<String, Double> weights = new LinkedHashMap<>();
    }

    /** 单项干预：op ∈ {SET, DELTA}（枚举字符串强校验由 Service 承担）。 */
    @Data
    public static class Intervention {
        /** 干预变量名（必填，须在该 domain 下有 belief 分布） */
        private String variableName;
        /** 干预方式：SET（强制覆写采点值）/ DELTA（outcome 数值平移） */
        private String op;
        /** 干预量：SET 为定点值；DELTA 为加减量（可负） */
        private double value;
    }
}
