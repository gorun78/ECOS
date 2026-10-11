package com.chinacreator.gzcm.engine.cognitive2.service.outcome;

import com.chinacreator.gzcm.engine.cognitive.api.context.CalculationResultRef;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 确定性计算产物只读接缝（《认知域与智能域补充详细设计 v1.0》§9.2 步骤 1/3/4、§9.3 末段铁律）。
 *
 * <p>数值主权：{@code predicted} / {@code actual} / 各维度贡献值一律由<b>确定性业务计算服务</b>产出，
 * 认知引擎只按 {@link CalculationResultRef} 取回引用指向的数值，<b>不得</b>自算、插值、外推或让 LLM 生成
 * （§0.3 红线 6 / B-2 / B-3）。计算产物本体落业务域 {@code ecos_dw}（铁律 §0.6 / ADR-14），
 * 本接缝只读，认知侧禁写业务域表（C.8 三层纪律）。</p>
 *
 * <p><b>无实现即 fail-loud</b>：与 {@code ForecastRunSnapshotReader} 同纪律 —— 本仓当前无业务侧
 * 计算服务实现 ⇒ 无本接口 bean ⇒ 调用方（{@code OutcomeEvaluateService}）把全部指标标为
 * 不适用并写明原因，评估状态压 {@code NEEDS_RECONCILE}，禁 200 返回内置/伪造数值（X-44）。
 * 业务侧落地时提供本接口 bean 即自动接通，认知侧不改代码。</p>
 */
public interface OutcomeMetricValueReader {

    /**
     * 按计算产物引用解析一组指标的预测值/实际值/贡献分解。
     *
     * <p>实现方必须<b>逐指标</b>给出结论：取不到就返回 {@code predicted}/{@code actual} 为 null
     * 且 {@code unavailableReason} 非空，禁返回 0 冒充（0 是合法业务值，与"取不到"必须可分，DG-6）。</p>
     */
    List<ResolvedValue> resolve(ResolveQuery query);

    /**
     * 解析请求（六要素锁定范围，禁全库扫描）。
     *
     * @param pairs 逐指标的预测侧/实际侧计算产物引用（引用可空 ⇒ 该侧无值）
     */
    record ResolveQuery(String scenarioId,
                        String runId,
                        String forecastRunId,
                        String caliberId,
                        String windowFrom,
                        String windowTo,
                        List<MetricRefPair> pairs) {
    }

    /** 一个指标的两侧计算产物引用。 */
    record MetricRefPair(String metricRef,
                         CalculationResultRef forecastRef,
                         CalculationResultRef actualRef) {
    }

    /**
     * 单指标解析结果。
     *
     * @param contributions 偏差按维度（项目/部门/期间/因素）的贡献分解，键 = 维度成员标识，
     *                      值 = 该成员贡献额（§9.2 步骤 4；可空 = 计算服务未提供分解）
     * @param unavailableReason 取不到值的原因（非空即表示该侧无值；禁为空串敷衍）
     */
    record ResolvedValue(String metricRef,
                         BigDecimal predicted,
                         BigDecimal actual,
                         String calculationRef,
                         Map<String, BigDecimal> contributions,
                         String unavailableReason) {

        public boolean fullyResolved() {
            return predicted != null && actual != null;
        }
    }
}
