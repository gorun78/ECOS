package com.chinacreator.gzcm.engine.cognitive2.service.backtest;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.backtest.BacktestMetricSummary;
import com.chinacreator.gzcm.engine.cognitive.api.backtest.BacktestReviewRouteResult;
import com.chinacreator.gzcm.engine.cognitive.api.backtest.DeviationDirection;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.MetricEvaluation;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluateResult;
import com.chinacreator.gzcm.engine.cognitive2.dao.OutcomeEvaluationDao;
import com.chinacreator.gzcm.engine.cognitive2.model.OutcomeEvaluationEntity;
import com.chinacreator.gzcm.engine.cognitive2.service.outcome.OutcomeEvaluateService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回测面装配服务（详设-09 v1.2 §十一 H-4 要点 1/2/3 的读侧落点）。
 *
 * <h3>为什么是"视图期装配"而不是改 {@code OutcomeEvaluateService}</h3>
 * <ul>
 *   <li><b>只增不改</b>：{@code OutcomeEvaluateService} 的构造签名与
 *       {@code POST /outcomes/evaluate} 的响应语义必须逐字不动（既有冻结测试以 7 参构造它），
 *       故回测三指标挂在本类，复用它的 {@link OutcomeEvaluateService#get(String)} 取<b>同一评估面</b>
 *       （MAE/MAPE 与三指标同源同分母，W225 要求"同一评估面"）；</li>
 *   <li>阈值与区间三值都在视图期读取，与仓库既有先例同纪律
 *       （{@code ForecastController#evalTrendWeight()} 每次调用读门面并回显来源）。</li>
 * </ul>
 *
 * <h3>诚实语义</h3>
 * 区间三值读缝（{@link ForecastIntervalTripleReader}）未装配 ⇒ 该行 P10/P90 为 {@code null}、
 * 进覆盖率排除集、{@code intervalValueSource} 明示 seam-absent；
 * <b>禁</b>由 P50 或邻值合成（R-62 ①），<b>禁</b>以空覆盖冒充 0 或 100%（DG-6/X-44）。
 */
@Service
public class BacktestSurfaceService {

    /** 三值读缝未装配时的定位串（进响应 {@code intervalValueSource}，与"源侧无三值"严格可分）。 */
    public static final String SEAM_ABSENT =
            "ForecastIntervalTripleReader bean absent (interval triple not wired):"
            + " P10/P90 stay null, samples go to intervalExcludedCount,"
            + " interpolation from P50 is forbidden (R-62 ①)";

    private final OutcomeEvaluateService outcomeEvaluateService;
    private final OutcomeEvaluationDao outcomeEvaluationDao;
    private final BacktestReviewThresholdReader thresholdReader;
    private final ObjectProvider<ForecastIntervalTripleReader> intervalReaderProvider;
    private final BacktestReviewRouter reviewRouter;

    public BacktestSurfaceService(OutcomeEvaluateService outcomeEvaluateService,
                                  OutcomeEvaluationDao outcomeEvaluationDao,
                                  BacktestReviewThresholdReader thresholdReader,
                                  ObjectProvider<ForecastIntervalTripleReader> intervalReaderProvider,
                                  BacktestReviewRouter reviewRouter) {
        this.outcomeEvaluateService = outcomeEvaluateService;
        this.outcomeEvaluationDao = outcomeEvaluationDao;
        this.thresholdReader = thresholdReader;
        this.intervalReaderProvider = intervalReaderProvider;
        this.reviewRouter = reviewRouter;
    }

    /**
     * 回测指标视图：既有评估结果（含 MAE/MAPE）+ 新增可空 {@code backtest} 段 + 行级三值/方向。
     *
     * @throws CognitiveGuardException 404（评估记录不存在）/ 503（三值读缝已装配但读取故障）
     */
    public OutcomeEvaluateResult view(String evaluationId) {
        OutcomeEvaluateResult result = outcomeEvaluateService.get(evaluationId);   // 400/404 已在其中裁决
        OutcomeEvaluationEntity entity = requireEntity(evaluationId);
        BacktestMetricCalculator.Thresholds thresholds = thresholdReader.read();
        BacktestMetricCalculator.Result computed = compute(result, entity, thresholds);
        result.setBacktest(computed.summary());
        // warnings 可能是不可变空列表（readJson 的 fallback），故整体替换而非原地 add，禁污染历史行。
        List<String> warnings = new ArrayList<>(result.getWarnings());
        warnings.addAll(computed.warnings());
        result.setWarnings(warnings);
        return result;
    }

    /**
     * 复审任务路由：以同一份阈值与同一评估面重算指标后交 {@link BacktestReviewRouter}。
     *
     * <p>阈值不从请求取（R-66 ①），角色不从本地映射取（铁律 §2.4）。</p>
     */
    public BacktestReviewRouteResult routeReview(String evaluationId, String traceId) {
        OutcomeEvaluateResult result = outcomeEvaluateService.get(evaluationId);
        OutcomeEvaluationEntity entity = requireEntity(evaluationId);
        BacktestMetricCalculator.Result computed =
                compute(result, entity, thresholdReader.read());
        return reviewRouter.route(entity, computed, traceId);
    }

    /** 三值读缝接线状态（供健康/preflight 面如实回显，false ≠ 假绿）。 */
    public boolean intervalSeamReady() {
        return intervalReaderProvider.getIfAvailable() != null;
    }

    /**
     * 核心装配：把既有 {@code metrics[]} 行原样搬成 {@link BacktestMetricCalculator.Sample}
     * （误差沿用既有确定性口径的 {@code relativeError}，<b>禁本类重算</b>），
     * 逐行回填区间三值与偏差方向（"三值全存全展"，R-62 ①）。
     */
    private BacktestMetricCalculator.Result compute(OutcomeEvaluateResult result,
                                                    OutcomeEvaluationEntity entity,
                                                    BacktestMetricCalculator.Thresholds thresholds) {
        Map<String, ForecastIntervalTripleReader.IntervalTriple> triples = readIntervalTriples(entity, result);
        List<BacktestMetricCalculator.Sample> samples = new ArrayList<>();
        for (MetricEvaluation m : result.getMetrics()) {
            ForecastIntervalTripleReader.IntervalTriple t = triples.get(m.getMetricRef());
            BigDecimalTriple bt = tripleOf(t);
            m.setPredictedP10(bt.p10());
            m.setPredictedP90(bt.p90());
            m.setIntervalValueSource(t == null ? SEAM_ABSENT : t.sourceRef());
            boolean bothSides = m.getPredicted() != null && m.getActual() != null;
            BacktestMetricCalculator.Sample sample = new BacktestMetricCalculator.Sample(
                    m.getMetricRef(), m.getPredicted(), m.getActual(),
                    bt.p10(), bt.p90(), m.getIntervalValueSource(), bothSides);
            DeviationDirection direction =
                    BacktestMetricCalculator.deviationDirection(sample, thresholds.onTargetBandRatio());
            m.setDeviationDirection(direction.name());
            m.setIntervalCovered(bt.p10() == null || bt.p90() == null || m.getActual() == null
                    ? null
                    : m.getActual().compareTo(bt.p10()) >= 0 && m.getActual().compareTo(bt.p90()) <= 0);
            samples.add(sample);
        }
        // MAPE 沿用同一评估面的既有聚合值（DeterministicErrorCalculator 产出），本类禁重算误差。
        BacktestMetricCalculator.Result computed = BacktestMetricCalculator.compute(
                samples, result.getTotalSampleCount(), thresholds, result.getMape());
        BacktestMetricSummary summary = computed.summary();
        if (summary.getIntervalValueSource() == null || summary.getIntervalValueSource().isBlank()) {
            summary.setIntervalValueSource(SEAM_ABSENT);
        }
        return computed;
    }

    /**
     * 读区间三值（按 {@code forecastRunId} + 本次声明的 metricRefs 精确取，禁全表扫描）。
     *
     * <p>未装配 / 该实现不承接本次 run ⇒ 返回空映射（行级如实标 seam-absent）；
     * 已装配但读缝报障 ⇒ 503 fail-loud，<b>禁</b>吞异常返回空集冒充"源侧没有三值"（SUP-CTX-03 同族）。</p>
     */
    private Map<String, ForecastIntervalTripleReader.IntervalTriple> readIntervalTriples(
            OutcomeEvaluationEntity entity, OutcomeEvaluateResult result) {
        ForecastIntervalTripleReader reader = intervalReaderProvider.getIfAvailable();
        if (reader == null || !reader.supports(entity.getForecastRunId())) {
            return Map.of();
        }
        List<String> refs = new ArrayList<>();
        for (MetricEvaluation m : result.getMetrics()) {
            if (m.getMetricRef() != null && !refs.contains(m.getMetricRef())) {
                refs.add(m.getMetricRef());
            }
        }
        ForecastIntervalTripleReader.IntervalQuery query = new ForecastIntervalTripleReader.IntervalQuery(
                entity.getForecastRunId(), entity.getScenarioId(), entity.getCaliberId(),
                entity.getWindowFromDate() == null ? null : entity.getWindowFromDate().toString(),
                entity.getWindowToDate() == null ? null : entity.getWindowToDate().toString(),
                List.copyOf(refs));
        Map<String, ForecastIntervalTripleReader.IntervalTriple> out = new LinkedHashMap<>();
        try {
            for (ForecastIntervalTripleReader.IntervalTriple t : reader.read(query)) {
                if (t != null && t.metricRef() != null) {
                    out.put(t.metricRef(), t);
                }
            }
        } catch (ForecastIntervalTripleReader.IntervalReadException e) {
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN,
                    "INTERVAL_TRIPLE_READ_FAILED: ForecastIntervalTripleReader read failed for runId="
                    + entity.getForecastRunId() + " cause=" + e.getClass().getSimpleName()
                    + " (interval coverage reported as unavailable, never synthesized)");
        }
        return out;
    }

    private OutcomeEvaluationEntity requireEntity(String evaluationId) {
        OutcomeEvaluationEntity entity = outcomeEvaluationDao.findById(evaluationId == null
                ? evaluationId : evaluationId.trim());
        if (entity == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_OUTCOME,
                    CognitiveErrorCode.MSG_OUTCOME_NOT_FOUND, "evaluationId=" + evaluationId);
        }
        return entity;
    }

    private static BigDecimalTriple tripleOf(ForecastIntervalTripleReader.IntervalTriple t) {
        return t == null
                ? new BigDecimalTriple(null, null)
                : new BigDecimalTriple(t.p10(), t.p90());
    }

    /** 行级区间上下界载体（避免在装配层反复解引用同一个可能为 null 的三值对象）。 */
    private record BigDecimalTriple(BigDecimal p10, BigDecimal p90) {
    }
}
