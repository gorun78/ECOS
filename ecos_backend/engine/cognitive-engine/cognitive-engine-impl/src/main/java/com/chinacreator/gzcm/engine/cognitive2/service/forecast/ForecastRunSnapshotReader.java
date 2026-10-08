package com.chinacreator.gzcm.engine.cognitive2.service.forecast;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ScenarioBalanceChecker;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预测运行服务只读接缝（详细设计-05 F05-09 要点2 / F05-10 要点1、要点4 / 接缝 S-6）。
 *
 * <p>金额唯一来源铁律（B-2 / B-3 / R-8 ②）：认知域<b>不得</b>自算或伪造金额，
 * {@code contribution} 与 {@code deltaProfit} 一律由本接缝取预测运行服务的<b>确定性运行快照</b>
 * 后复算归因。快照读端点属 09 册 FC-02（{@code GET /api/v1/workspace/forecast-runs/{runId}}）、
 * 情景运行属 FC-03（{@code POST /api/v1/workspace/forecast-runs/{runId}/scenario-copy}），
 * 产物落 {@code ecos_dw.ecos_application_forecast_run}（V197 / R-17 ①）经 data-engine 写通道。</p>
 *
 * <p><b>无实现即 fail-loud</b>：本期仓内无 09 册运行服务实现 ⇒ 无本接口 bean，
 * 调用方（{@code ForecastDiagnosisService}）一律回 DG-9 语义（真实 501 +
 * {@code ECOS-COG-503} + {@code data.stub=true} + {@code data.missing}），
 * 禁 200 + 内置/伪造数值（X-44）。09 册落地时提供本接口 bean 即自动接通，认知侧不改代码。</p>
 *
 * <p>三层纪律（C.8）：实现方只读，不得在认知侧写业务域表（F05-10 要点5）。</p>
 */
public interface ForecastRunSnapshotReader {

    /**
     * FC-02 只读运行快照。查询必须带 {@code forecastRunId} 定位（要点4：锁定快照范围，
     * 禁全库扫描）；{@code projectId}/{@code departmentId} 非空时进一步限定命中范围。
     */
    RunSnapshot getRun(SnapshotQuery query);

    /**
     * FC-03 情景运行：以 {@code baselineRunId + overrides} 触发预测运行服务确定性重算，
     * 返回<b>新</b> forecastRunId（满足六要素：独立运行键 + 快照 + 口径版本）。
     * 认知侧只发起、不自算。
     */
    String copyScenario(ScenarioCopyRequest request);

    /** 快照定位六要素（与 V197 caliber_id/caliber_version/as_of_time/project_id/department_id 同源）。 */
    record SnapshotQuery(String forecastRunId,
                         String caliberId,
                         String caliberVersion,
                         String asOfTime,
                         String projectId,
                         String departmentId) {
        public static SnapshotQuery of(CognitiveContractSchema.ForecastContextVO ctx) {
            return new SnapshotQuery(ctx.getForecastRunId(), ctx.getCaliberId(), ctx.getCaliberVersion(),
                    ctx.getAsOfTime(), ctx.getProjectId(), ctx.getDepartmentId());
        }
    }

    /** 一次运行的只读快照：金额 cell 明细 + 因果边明细 + 两个利润真值。 */
    record RunSnapshot(String forecastRunId,
                       String caliberId,
                       String caliberVersion,
                       String asOfTime,
                       BigDecimal baselineProfit,
                       BigDecimal scenarioProfit,
                       List<ScenarioBalanceChecker.Cell> cells,
                       List<ContributionEdgeSource> edges) {
    }

    /**
     * 快照因果边（结构化）。{@code evidenceRefs}/{@code statSupport} 的"有证据"判定与
     * {@link com.chinacreator.gzcm.engine.cognitive2.service.calc.ContributionCalculator.SnapshotEdge}
     * 同口径，由 {@code ForecastDiagnosisService} 转换后复用纯函数。
     */
    record ContributionEdgeSource(String chainId,
                                 List<CognitiveContractSchema.ChainNodeVO> path,
                                 BigDecimal baseline,
                                 BigDecimal scenario,
                                 List<String> evidenceRefs,
                                 CognitiveContractSchema.StatSupportVO statSupport) {
    }

    /** FC-03 情景复制请求：overrides 结构冻结（F05-10 要点1），只覆盖命中范围。 */
    record ScenarioCopyRequest(String baselineRunId,
                               List<CognitiveContractSchema.SimulateOverrideVO> overrides) {
        /** 命中项目集合（用于 {@code onlyOverriddenScopeDiffs} 归因泄漏校验）。 */
        public List<String> overriddenProjectIds() {
            if (overrides == null) {
                return List.of();
            }
            return overrides.stream()
                    .map(CognitiveContractSchema.SimulateOverrideVO::getProjectId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
        }
    }
}
