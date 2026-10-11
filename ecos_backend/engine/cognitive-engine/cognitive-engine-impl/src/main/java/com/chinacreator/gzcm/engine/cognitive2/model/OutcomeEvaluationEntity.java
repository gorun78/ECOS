package com.chinacreator.gzcm.engine.cognitive2.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * W→D 结果评估数据库实体 —— 对应 {@code ecos_cognitive.ecos_cognitive_outcome_evaluation}
 * （V267 建表 + V274 补三列：{@code idempotency_key} / {@code blockers_text} / {@code caliber_alignment}）。
 *
 * <p>《认知域与智能域补充详细设计 v1.0》§9 落库红线：本实体<b>只承载评估记录本身</b>。
 * {@code metricResultsJson} 内的 predicted/actual/误差数值全部由确定性业务计算服务产生
 * （§9.3 末段铁律），认知引擎只组织引用；{@code updateCandidatesJson} 只存候选<b>引用</b>与状态，
 * 禁在此改写任何生产 Belief/Rule/Model（§9.4 防错误学习，SUP-OUT-01）。</p>
 *
 * <p>JSON 列一律 TEXT 形态（MC02 禁 jsonb），窗口列为 {@code DATE}（无时区，MC03）。</p>
 */
@Data
public class OutcomeEvaluationEntity {

    /** 评估记录 ID（MC01 应用侧 UUID）。 */
    private String id;

    private String tenantId;

    private String orgId;

    /** 场景 ID（逻辑引用 workspace 场景，ST09 禁跨 schema FK）。 */
    private String scenarioId;

    private String blueprintId;

    private String blueprintVersion;

    /** 本次业务运行 ID（§9.1「任务执行完了」的那一次）。 */
    private String runId;

    /** 预测运行快照引用（§9.2 步骤 1）。 */
    private String forecastRunId;

    /** 实际值权威数据资产引用（正文落业务域，本处只存引用）。 */
    private String actualAssetId;

    private String actualVersion;

    /** 对齐口径 ID（§9.2 步骤 2）。 */
    private String caliberId;

    /** §9.2 步骤 2 口径对齐结论（V274）。 */
    private String caliberAlignment;

    private LocalDate windowFromDate;

    private LocalDate windowToDate;

    /** PENDING / EVALUATED / NEEDS_RECONCILE / REJECTED（词表单源 = {@code OutcomeEvaluationStatus}）。 */
    private String status;

    /** JSON 数组文本：逐指标 predicted/actual/absoluteError/relativeError/calculationRef。 */
    private String metricResultsJson;

    /** JSON 数组文本：认知诊断（引用当时 Hypothesis/Belief/CM 版本）。 */
    private String diagnosesJson;

    /** JSON 数组文本：仅候选引用（candidateType/candidateRef/status）。 */
    private String updateCandidatesJson;

    /** JSON 数组文本：样本不适用原因、门禁失败原因等（§9.4）。 */
    private String warningsJson;

    /** JSON 数组文本：§9.4 命中的禁自更新情形枚举名（V274）。 */
    private String blockersJson;

    /** JSON 数组文本：确定性计算产物引用（CalculationResultRef 全量，REQ-COG-09）。 */
    private String calculationRefsJson;

    private String traceId;

    /** 幂等键（V274；同键重试返回既有记录）。 */
    private String idempotencyKey;

    private String versionNo;

    private String createBy;

    private String updateBy;
}
