package com.chinacreator.gzcm.engine.cognitive2.service.forecast;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.observability.CognitiveEventLog;
import com.chinacreator.gzcm.engine.cognitive2.observability.CognitiveObservability;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ContributionCalculator;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ScenarioBalanceChecker;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.scheduler.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 领域化诊断 / 确定性情景重算的编排服务（详细设计-05 F05-09 / F05-10；C.8 三层纪律：
 * Controller 不做业务计算，Service 只做编排）。
 *
 * <p>数值主权（B-2 / B-3）：{@code contribution}、{@code deltaProfit} 全部由
 * {@link ForecastRunSnapshotReader} 取预测运行服务只读快照后交
 * {@link ContributionCalculator} / {@link ScenarioBalanceChecker} 纯函数确定性复算，
 * LLM 只产出 {@code explanation} 文本且提示词不含任何数值。</p>
 *
 * <p>DG-8：llm-gateway 不可用（bean 缺失 / profile 未注册 / 调用异常）→
 * {@code explanationUnavailable=true} + 200，数值与链路正常返回，禁因 LLM 缺失而伪造或置空。</p>
 *
 * <p>DG-9：09 册 FC-02/FC-03 未上线 ⇒ 无 {@link ForecastRunSnapshotReader} bean ⇒
 * 抛 {@code ECOS-COG-503}（真实 501 + {@code data.stub=true} + {@code data.missing}），
 * 禁伪造快照冒充"预测运行服务"。</p>
 */
@Service
public class ForecastDiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(ForecastDiagnosisService.class);

    /** llm-gateway 归属子系统（与 {@code SuggestionBuilder} 同源）。 */
    private static final String LLM_SUBSYSTEM = "aiming";
    /** 解释文本 profile（无数值生成职责；未注册即走 DG-8 降级）。 */
    private static final String LLM_PROFILE_EXPLAIN = "cognitive-forecast-explain";

    /** 09 册接缝未交付时的 fail-loud 定位串（进 {@code data.missing}）。 */
    static final String MISSING_FC02 = "09册FC-02 forecast-run 只读快照端点(ForecastRunSnapshotReader bean)";
    static final String MISSING_FC03 = "09册FC-03 forecast-runs scenario-copy 确定性重算端点";

    private final ObjectProvider<ForecastRunSnapshotReader> snapshotReaderProvider;
    private final ObjectProvider<LLMGatewayService> llmGatewayProvider;
    private final CognitiveObservability observability;

    public ForecastDiagnosisService(ObjectProvider<ForecastRunSnapshotReader> snapshotReaderProvider,
                                    ObjectProvider<LLMGatewayService> llmGatewayProvider,
                                    CognitiveObservability observability) {
        this.snapshotReaderProvider = snapshotReaderProvider;
        this.llmGatewayProvider = llmGatewayProvider;
        this.observability = observability;
    }

    /**
     * F05-09：{@code forecastContext} 存在时的领域化因果链装配。
     * 快照范围由六要素锁定（要点4，禁全库扫描）；结果不落库（要点5）。
     */
    public CognitiveContractSchema.DiagnoseForecastData diagnose(
            CognitiveContractSchema.ForecastContextVO ctx) {
        ForecastRunSnapshotReader reader = requireReader(MISSING_FC02);
        ForecastRunSnapshotReader.RunSnapshot snapshot =
                reader.getRun(ForecastRunSnapshotReader.SnapshotQuery.of(ctx));
        assertScopeLocked(ctx, snapshot);

        CognitiveContractSchema.DiagnoseForecastData data =
                new CognitiveContractSchema.DiagnoseForecastData();
        data.setChains(ContributionCalculator.chains(toSnapshotEdges(snapshot)));
        String explanation = buildExplanation(data.getChains());
        if (explanation == null) {
            data.setExplanationUnavailable(true);
        } else {
            data.setExplanation(explanation);
            data.setExplanationUnavailable(false);
        }
        log.info("领域化诊断链装配完成: forecastRunId={}, chains={}",
                snapshot.forecastRunId(), data.getChains().size());
        observability.audit("cognitiveForecast.diagnose", "forecast_run",
                "forecastRunId=" + snapshot.forecastRunId() + " chains=" + data.getChains().size());
        return data;
    }

    /**
     * F05-10：确定性重算 + 归因平衡自检。
     * 平衡失守（{@code |sum(contribution) − (scenarioProfit − baselineProfit)| > 0.01}）
     * 或命中范围外出现非零 diff（归因泄漏）→ 抛 {@code ECOS-COG-502}（真实 503），
     * 禁返回不平数据（要点3）。
     */
    public CognitiveContractSchema.SimulateResponseVO simulateDeterministic(
            String baselineRunId, List<CognitiveContractSchema.SimulateOverrideVO> overrides) {
        ForecastRunSnapshotReader reader = requireReader(MISSING_FC03);
        ForecastRunSnapshotReader.ScenarioCopyRequest copyRequest =
                new ForecastRunSnapshotReader.ScenarioCopyRequest(baselineRunId, overrides);
        String scenarioRunId = reader.copyScenario(copyRequest);
        ForecastRunSnapshotReader.RunSnapshot snapshot = reader.getRun(
                new ForecastRunSnapshotReader.SnapshotQuery(scenarioRunId, null, null, null, null, null));

        List<ScenarioBalanceChecker.Cell> cells =
                snapshot.cells() == null ? List.of() : snapshot.cells();
        if (!ScenarioBalanceChecker.onlyOverriddenScopeDiffs(cells, copyRequest.overriddenProjectIds())) {
            observability.counter(CognitiveEventLog.METRIC_BALANCE_FAIL, 1);
            throw new CognitiveGuardException(CognitiveErrorCode.E502_BALANCE_NOT_OK,
                    CognitiveErrorCode.MSG_NOT_BALANCED, "归因泄漏：命中范围外 diff≠0");
        }
        BigDecimal baselineProfit = nz(snapshot.baselineProfit());
        BigDecimal scenarioProfit = nz(snapshot.scenarioProfit());
        if (!ScenarioBalanceChecker.isBalanced(cells, baselineProfit, scenarioProfit)) {
            observability.counter(CognitiveEventLog.METRIC_BALANCE_FAIL, 1);
            throw new CognitiveGuardException(CognitiveErrorCode.E502_BALANCE_NOT_OK,
                    CognitiveErrorCode.MSG_NOT_BALANCED, "sum(contribution) 与净利差超容差 ±0.01");
        }

        CognitiveContractSchema.SimulateResponseVO vo = new CognitiveContractSchema.SimulateResponseVO();
        vo.setMode(CognitiveContractSchema.MODE_DETERMINISTIC_DIFF);
        vo.setScenarioRunId(scenarioRunId);
        vo.setBalanceOk(true);
        vo.setContributionsSum(sum(ScenarioBalanceChecker.contributions(cells)));
        vo.setDeltaProfit(scenarioProfit.subtract(baselineProfit).setScale(4, java.math.RoundingMode.HALF_UP));
        vo.setTolerance(CognitiveContractSchema.BALANCE_TOLERANCE);
        vo.setChains(ContributionCalculator.chains(toSnapshotEdges(snapshot)));
        log.info("确定性情景重算归因完成: baseline={}, scenario={}, deltaProfit={}",
                baselineRunId, scenarioRunId, vo.getDeltaProfit());
        observability.audit("cognitiveForecast.simulate", "forecast_scenario_run",
                "baselineRunId=" + baselineRunId + " scenarioRunId=" + scenarioRunId
                        + " deltaProfit=" + vo.getDeltaProfit());
        return vo;
    }

    /** 快照可用性判定（供健康面/preflight 使用；无 bean = 09 册接缝未接通，非假绿）。 */
    public boolean snapshotSeamReady() {
        return snapshotReaderProvider.getIfAvailable() != null;
    }

    private ForecastRunSnapshotReader requireReader(String missing) {
        ForecastRunSnapshotReader reader = snapshotReaderProvider.getIfAvailable();
        if (reader == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E503_NOT_IMPLEMENTED,
                    CognitiveErrorCode.MSG_NOT_IMPLEMENTED, missing);
        }
        return reader;
    }

    /** 要点4：请求上下文与返回快照必须同源，禁拿别的运行/口径冒充本次诊断。 */
    private static void assertScopeLocked(CognitiveContractSchema.ForecastContextVO ctx,
                                          ForecastRunSnapshotReader.RunSnapshot snapshot) {
        if (snapshot == null || snapshot.forecastRunId() == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                    CognitiveErrorCode.MSG_INTERNAL, "快照缺少 forecastRunId");
        }
        if (ctx.getForecastRunId() != null && !ctx.getForecastRunId().equals(snapshot.forecastRunId())) {
            throw new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                    CognitiveErrorCode.MSG_INTERNAL,
                    "快照运行键与请求 forecastContext 不一致: " + snapshot.forecastRunId());
        }
    }

    private static List<ContributionCalculator.SnapshotEdge> toSnapshotEdges(
            ForecastRunSnapshotReader.RunSnapshot snapshot) {
        List<ContributionCalculator.SnapshotEdge> edges = new ArrayList<>();
        if (snapshot.edges() == null) {
            return edges;
        }
        for (ForecastRunSnapshotReader.ContributionEdgeSource e : snapshot.edges()) {
            validateProbability(e.statSupport() == null ? null : e.statSupport().getPValue());
            edges.add(new ContributionCalculator.SnapshotEdge(e.chainId(), e.path(),
                    nz(e.baseline()), nz(e.scenario()), e.evidenceRefs(), e.statSupport()));
        }
        return edges;
    }

    /** D.2 规则4：p 值 ∈ [0,1]，越界即数据缺陷 → 500（禁 clamp 静默）。 */
    private static void validateProbability(Double pValue) {
        if (pValue == null) {
            return;
        }
        if (pValue < 0.0 || pValue > 1.0) {
            throw new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                    CognitiveErrorCode.MSG_INTERNAL, "statSupport.pValue 越界: " + pValue);
        }
    }

    /**
     * DG-8：解释文本唯一经 llm-gateway（铁律 §2.5-2）；提示词只含节点名，禁投数值，
     * 因此 LLM 无从"生成数值"。任何不可用形态降级为 {@code null}（→ explanationUnavailable=true），
     * 禁抛穿污染数值面，也禁内置文案冒充 LLM 输出。
     */
    private String buildExplanation(List<CognitiveContractSchema.DiagnoseChainVO> chains) {
        LLMGatewayService gateway = llmGatewayProvider.getIfAvailable();
        if (gateway == null || chains == null || chains.isEmpty()) {
            return null;
        }
        StringBuilder nodes = new StringBuilder();
        for (CognitiveContractSchema.DiagnoseChainVO chain : chains) {
            if (chain.getPath() == null) {
                continue;
            }
            chain.getPath().forEach(node -> nodes.append(node.getNode()).append(" > "));
            nodes.append('\n');
        }
        if (nodes.length() == 0) {
            return null;
        }
        String prompt = "请用中文概述以下经营因果链的业务含义（只叙述机制与影响路径，"
                + "禁止输出任何数字、金额、百分比）：\n" + nodes;
        try {
            AgentResult result = gateway.execute(LLM_SUBSYSTEM, LLM_PROFILE_EXPLAIN, prompt);
            if (result == null || !result.isSuccess() || result.getContent() == null
                    || result.getContent().isBlank()) {
                log.warn("DG-8 解释降级: llm-gateway 返回不可用 (profile={})", LLM_PROFILE_EXPLAIN);
                return null;
            }
            return result.getContent();
        } catch (Exception e) {
            log.warn("DG-8 解释降级: llm-gateway 调用异常 {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        BigDecimal acc = BigDecimal.ZERO;
        for (BigDecimal v : values) acc = acc.add(v);
        return acc.setScale(4, java.math.RoundingMode.HALF_UP);
    }
}
