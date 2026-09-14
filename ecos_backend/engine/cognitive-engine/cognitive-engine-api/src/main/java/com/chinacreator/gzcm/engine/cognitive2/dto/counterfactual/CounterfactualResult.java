package com.chinacreator.gzcm.engine.cognitive2.dto.counterfactual;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 反事实推演结果 DTO（PMO-59 P3a / ADR-9 心智层推演核心）。
 *
 * <p>输出契约（指令 T1）：
 * <ul>
 *   <li>风险四指标 {@link #riskMetrics}：expectedBenefit（干预组均值−基线组均值）/
 *       maxDrawdown（max(0, 中位数−最小值)，干预组）/ lossProbability（配对逐样本亏损占比）/
 *       volatilityRange（干预组 p05~p95 波动区间）；</li>
 *   <li>敏感性 {@link #sensitivityTop3}：对每个输入变量单独扰动，按 |ΔE[metric]|/|扰动量| 降序取 Top3；</li>
 *   <li>推演前提留痕：{@link #assumptionRefs}（当时 VALID 假设 id）/
 *       {@link #excludedAssumptions}（已失效被排除的假设 id，失效假设不进入推演前提）。</li>
 * </ul>
 *
 * <p>结果<b>不落盘</b>（ADR-9 三档口径：推理结果实时计算）；调用方（workspace SAFEGUARD）
 * 如需留痕自行落 {@code ecos_scenario_run.simulation_result}。</p>
 */
@Data
public class CounterfactualResult {

    /** 请求回显（variableName/domain/sampleCount/seed，结果可溯源）。 */
    private RequestEcho requestEcho = new RequestEcho();

    /** 基线组指标均值（干预组为相对它的 Δ）。 */
    private double baselineMean;

    /** 干预组指标均值。 */
    private double intervenedMean;

    /** 风险四指标。 */
    private RiskMetrics riskMetrics = new RiskMetrics();

    /** 敏感性 Top3（降序）。 */
    private List<SensitivityItem> sensitivityTop3 = new ArrayList<>();

    /** 推演前提：当时 VALID 假设 id 列表（domain 下 status=VALID 且 is_valid=true）。 */
    private List<String> assumptionRefs = new ArrayList<>();

    /** 被排除假设（失效/归档，不进入推演前提；复盘与作废联动溯源用）。 */
    private List<ExcludedAssumption> excludedAssumptions = new ArrayList<>();

    /** 实际采样次数（请求有效值）。 */
    private int sampleCount;

    /** 实际使用的随机种子（可复现凭证）。 */
    private long seed;

    /** 主变量名（结果口径命名回显）。 */
    private String variableName;

    /** 占位指标摘要（按主变量口径的一句话数值描述，纯模板生成，零 LLM）。 */
    private String scenarioSummary;

    /** 请求回显。 */
    @Data
    public static class RequestEcho {
        /** 场景 id（可选回显） */
        private String scenarioId;
        /** 业务域 */
        private String domain;
        /** 主变量名 */
        private String variableName;
        /** 有效采样次数 */
        private int sampleCount;
        /** 有效种子 */
        private long seed;
        /** 干预摘要：{variableName: "SET=1.5" | "DELTA=0.15"} */
        private Map<String, String> interventions = new LinkedHashMap<>();
    }

    /** 风险四指标（纯 Java 数值，禁止 LLM 参与计算）。 */
    @Data
    public static class RiskMetrics {
        /** 期望收益：mean(interv) − mean(base) */
        private double expectedBenefit;
        /** 最大回撤：max(0, median(interv) − min(interv)) */
        private double maxDrawdown;
        /** 亏损概率：count(metric_interv_t < metric_base_t) / N（配对逐样本） */
        private double lossProbability;
        /** 波动区间：干预组 [p05, p95] */
        private double[] volatilityRange = new double[2];
    }

    /** 敏感性单条：输入变量 + 敏感度系数 |ΔE[metric]| / |扰动量|。 */
    @Data
    public static class SensitivityItem {
        /** 输入变量名（belief variable_name） */
        private String variable;
        /** 敏感度系数（越大越敏感） */
        private double sensitivity;

        public SensitivityItem() {
        }

        public SensitivityItem(String variable, double sensitivity) {
            this.variable = variable;
            this.sensitivity = sensitivity;
        }
    }

    /** 被排除假设摘要（id + 状态 + 失效原因摘要）。 */
    @Data
    public static class ExcludedAssumption {
        /** 假设主键 */
        private String hypothesisId;
        /** 当前状态（INVALIDATED / ARCHIVED） */
        private String status;
        /** 失效原因摘要（可 null） */
        private String invalidReason;

        public ExcludedAssumption() {
        }

        public ExcludedAssumption(String hypothesisId, String status, String invalidReason) {
            this.hypothesisId = hypothesisId;
            this.status = status;
            this.invalidReason = invalidReason;
        }
    }
}
