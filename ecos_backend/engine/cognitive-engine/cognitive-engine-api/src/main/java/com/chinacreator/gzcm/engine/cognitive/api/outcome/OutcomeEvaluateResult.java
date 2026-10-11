package com.chinacreator.gzcm.engine.cognitive.api.outcome;

import com.chinacreator.gzcm.engine.cognitive.api.backtest.BacktestMetricSummary;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * W→D 结果评估响应体（《认知域与智能域补充详细设计 v1.0》§9.3 {@code data} 段）。
 *
 * <p>逐字段对齐 §9.3 示例：{@code evaluationId} / {@code status} / {@code metrics[]} /
 * {@code diagnoses[]} / {@code updateCandidates[]} / {@code warnings[]}。外层
 * {@code code}/{@code data} 包装由 {@code ApiResponse} 承载，本类只是 {@code data}。</p>
 *
 * <h3>诚实语义（DG-6 / §9.1）</h3>
 * <ul>
 *   <li>{@code status=EVALUATED} 只在口径对齐通过且确定性计算产出全部指标时出现；</li>
 *   <li>命中 §9.4 任一门禁 ⇒ {@code status=NEEDS_RECONCILE} 且 {@code blockers[]} 非空，
 *       <b>不得</b>返回 EVALUATED（禁把"评估没做成"包装成"评估通过"）；</li>
 *   <li>{@code updateCandidates[]} 恒为<b>候选引用</b>，非空也不代表生产资产已改
 *       （SUP-OUT-01：单次偏差只产候选，生产写次数 0）。</li>
 * </ul>
 */
@Data
public class OutcomeEvaluateResult {

    /** 评估记录 ID（V267 主键，应用侧 UUID）。 */
    private String evaluationId;

    /** 评估状态（{@link OutcomeEvaluationStatus} 词表）。 */
    private String status;

    /** 逐指标评估结果（数值全部来自确定性计算产物引用）。 */
    private List<MetricEvaluation> metrics = new ArrayList<>();

    /** 认知诊断（§9.2 步骤 5；引用当时资产版本，禁复制正文）。 */
    private List<OutcomeDiagnosis> diagnoses = new ArrayList<>();

    /** 更新建议候选引用（§9.2 步骤 6；全部 Candidate/Proposal）。 */
    private List<UpdateCandidateRef> updateCandidates = new ArrayList<>();

    /** 告警/不适用原因（§9.3；样本不适用、门禁失败原因等如实登记，禁空串敷衍）。 */
    private List<String> warnings = new ArrayList<>();

    /** 命中的 §9.4 禁自更新情形（空 = 未命中；非空 ⇒ status 必为 NEEDS_RECONCILE）。 */
    private List<OutcomeEvaluationBlocker> blockers = new ArrayList<>();

    /** 口径对齐结论（§9.2 步骤 2；未对齐时说明差异维度）。 */
    private String caliberAlignment;

    /** 确定性计算产物引用回显（可复算锚点，SUP-OUT-02）。 */
    private List<String> calculationRefs = new ArrayList<>();

    /** 幂等命中标记（true = 本次返回既有评估记录，未重复计算）。 */
    private boolean idempotentReplay;

    /** 追踪 ID 回显（§12.2）。 */
    private String traceId;

    // ══════════ §9.2 步骤 3 聚合误差（§9.3 示例未列，属"逻辑结构"增补；禁伪造） ══════════

    /** 平均绝对误差 MAE（仅对可用样本求均；无可用样本 ⇒ null，禁写 0 冒充）。 */
    private BigDecimal mae;

    /** 平均绝对百分比误差 MAPE（同上；实际值为 0 的样本不入分母）。 */
    private BigDecimal mape;

    /** 请求评估的指标总数（含不适用项，禁静默丢弃）。 */
    private int totalSampleCount;

    /** 预测值与实际值双侧齐备、真正参与误差计算的样本数。 */
    private int applicableSampleCount;

    /** 偏差贡献分解自检结论（§9.2 步骤 4；无贡献输入时为 null，不平则如实登记残差）。 */
    private List<String> decompositions = new ArrayList<>();

    /** 是否命中任一 §9.4 门禁。 */
    public boolean hasBlockers() {
        return blockers != null && !blockers.isEmpty();
    }

    // ══════════ 详设-09 v1.2 §十一 H-4 追加（2026-10-12）：回测三指标 + LOW_SAMPLE 守卫 ══════════
    // 与上方 mae/mape/totalSampleCount/applicableSampleCount 同处一个评估面（同一评估记录、同一响应体），
    // 只增本字段、不改上方任何既有字段名与语义；null = 本次调用未做回测判定（禁以空对象冒充"已回测"）。

    /** 回测指标面（偏差方向 / 区间覆盖率 / 数据覆盖率 / {@code LOW_SAMPLE} 守卫 + 阈值生效来源）。 */
    private BacktestMetricSummary backtest;
}
