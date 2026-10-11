package com.chinacreator.gzcm.engine.cognitive2.service.outcome;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.context.CalculationResultRef;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.MetricCalculationBinding;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.MetricEvaluation;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeDiagnosis;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluateRequest;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluateResult;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluationBlocker;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluationStatus;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.UpdateCandidateRef;
import com.chinacreator.gzcm.engine.cognitive2.dao.BeliefDao;
import com.chinacreator.gzcm.engine.cognitive2.dao.HypothesisDao;
import com.chinacreator.gzcm.engine.cognitive2.dao.OutcomeEvaluationDao;
import com.chinacreator.gzcm.engine.cognitive2.model.OutcomeEvaluationEntity;
import com.chinacreator.gzcm.engine.cognitive2.observability.CognitiveObservability;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * W→D 结果验证与认知反馈编排服务（《认知域与智能域补充详细设计 v1.0》§9；
 * 端点 {@code POST /api/v1/cognitive/outcomes/evaluate}）。
 *
 * <h3>§9.1 语义分工</h3>
 * Run 成功 ≠ 业务结果验证过。本服务把两者分开：它<b>不</b>判断"任务跑完了没有"，
 * 只判断"预测对不对、假设成不成立、模型能不能更新"。
 *
 * <h3>§9.2 十步在本服务的落点</h3>
 * <ol>
 *   <li>收集结果 —— {@code req} 的运行/实际值/窗口/口径引用 + 逐指标计算产物绑定；</li>
 *   <li>对齐口径 —— {@link #alignCaliber}，不对齐 ⇒ {@code CALIBER_MISMATCH}；</li>
 *   <li>计算误差 —— {@link OutcomeMetricValueReader} 取确定性产物 → {@link DeterministicErrorCalculator}；</li>
 *   <li>分解偏差 —— 同上 reader 的 {@code contributions} → {@link DeterministicErrorCalculator#decompose}，
 *       <b>不平只登记残差，绝不改贡献值去凑平</b>；</li>
 *   <li>认知诊断 —— {@link #diagnose}，关联当时 Hypothesis / Belief 行（真库读，非编造）；</li>
 *   <li>生成更新建议 —— {@link #buildCandidates}，<b>全部是候选引用</b>；</li>
 *   <li>影响分析 —— 无下游影响数据源 ⇒ 如实登记 warning，禁伪造影响面；</li>
 *   <li>回归/回测/校准 —— 由 §9.4 {@code BASELINE_REGRESSION_INCOMPLETE} 门禁承载，本端点不执行回归；</li>
 *   <li>审批与发布 —— <b>不在本端点</b>：候选只落引用，正式更新走 security-engine 审批 + 新版本；</li>
 *   <li>闭环记录 —— {@link OutcomeEvaluationDao#insert}（V267 + V274）+ {@code OutcomeEvaluated} 事件。</li>
 * </ol>
 *
 * <h3>红线</h3>
 * <ul>
 *   <li><b>SUP-OUT-01</b>：单次偏差不得自动升格生产 Belief/Rule/Model —— 本服务对
 *       {@code ecos_cognitive_belief} / {@code ecos_cognitive_hypothesis} / {@code ecos_cognitive_model}
 *       <b>零写入</b>，只读；{@code updateCandidates[]} 恒为引用。</li>
 *   <li><b>SUP-OUT-02 / §9.3 末段</b>：数值只出自确定性计算产物；LLM 零参与
 *       （本服务不注入 {@code LLMGatewayService}，连"解释文本"也不由 LLM 生成，
 *       因为解释一旦带数值归因就等于让 LLM 产数值）。</li>
 *   <li><b>§9.4 fail-loud</b>：七情形任一命中 ⇒ {@code NEEDS_RECONCILE} + {@code blockers[]} 非空，
 *       <b>不得</b>返回 {@code EVALUATED}；声明缺失（null）一律按未满足处理。</li>
 *   <li><b>DG-9</b>：无 {@link OutcomeMetricValueReader} bean ⇒ 全部指标 {@code applicable=false}
 *       并写明缺哪个接缝，禁 200 返回内置/伪造数值。</li>
 * </ul>
 */
@Service("outcomeEvaluateService")
public class OutcomeEvaluateService {

    private static final Logger log = LoggerFactory.getLogger(OutcomeEvaluateService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            // 历史行可能带着后来删掉的键；读侧宽容、写侧严格（写侧仍全字段落库）
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP =
            new TypeReference<>() { };
    private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() { };

    /** 口径对齐结论前缀（V274 {@code caliber_alignment} 列词表）。 */
    static final String CALIBER_ALIGNED = "ALIGNED";
    static final String CALIBER_MISMATCH_PREFIX = "MISMATCH:";
    static final String CALIBER_UNCONFIRMED_PREFIX = "UNCONFIRMED:";

    /** 确定性数值接缝未交付时的 fail-loud 定位串（进 warnings，DG-9）。 */
    static final String MISSING_VALUE_SEAM =
            "deterministic metric value seam not deployed (OutcomeMetricValueReader bean absent): "
            + "predicted/actual must come from the deterministic business calculation service, "
            + "cognitive engine never computes or fabricates them";

    /** §9.2 步骤 7 无数据源时的如实登记（禁伪造影响面）。 */
    static final String IMPACT_ANALYSIS_UNAVAILABLE =
            "IMPACT_ANALYSIS_UNAVAILABLE: no downstream blueprint/run/scenario/model impact index in this "
            + "repository; affected assets must be resolved by the impact service, not guessed here";

    /** §9.2 步骤 5 中无法关联的资产类别（禁编造版本引用）。 */
    static final String DECISION_BASIS_UNLINKED =
            "DECISION_BASIS_UNLINKED: request carries no decisionBasisRef and cognitive domain has no "
            + "decision-basis index by metric; version linkage omitted rather than invented";
    static final String MODEL_VERSION_UNLINKED =
            "COGNITIVE_MODEL_UNLINKED: request carries no modelRef; the model version used at forecast time "
            + "cannot be resolved from metricRef alone, linkage omitted rather than invented";

    private final OutcomeEvaluationDao outcomeDao;
    private final OutcomeGuardEvaluator guardEvaluator;
    private final HypothesisDao hypothesisDao;
    private final BeliefDao beliefDao;
    private final ObjectProvider<OutcomeMetricValueReader> valueReaderProvider;
    private final ObjectProvider<EventBusService> eventBusProvider;
    private final CognitiveObservability observability;

    public OutcomeEvaluateService(OutcomeEvaluationDao outcomeDao,
                                  OutcomeGuardEvaluator guardEvaluator,
                                  HypothesisDao hypothesisDao,
                                  BeliefDao beliefDao,
                                  ObjectProvider<OutcomeMetricValueReader> valueReaderProvider,
                                  ObjectProvider<EventBusService> eventBusProvider,
                                  CognitiveObservability observability) {
        this.outcomeDao = outcomeDao;
        this.guardEvaluator = guardEvaluator;
        this.hypothesisDao = hypothesisDao;
        this.beliefDao = beliefDao;
        this.valueReaderProvider = valueReaderProvider;
        this.eventBusProvider = eventBusProvider;
        this.observability = observability;
    }

    /** 确定性数值接缝是否已接通（供健康面/preflight 使用；false ≠ 假绿）。 */
    public boolean valueSeamReady() {
        return valueReaderProvider.getIfAvailable() != null;
    }

    // ═══════════════════════ §9.2 主流程 ═══════════════════════

    /**
     * 执行一次 W→D 结果评估并落闭环记录。
     *
     * @throws CognitiveGuardException 请求结构性非法（{@code ECOS-COG-400O} → 400）
     */
    public OutcomeEvaluateResult evaluate(OutcomeEvaluateRequest req) {
        // ── 步骤 0：结构校验（§9.3 必填 / 引用三要素）──
        List<String> problems = req == null ? List.of("request body is null") : req.validate();
        if (!problems.isEmpty()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_OUTCOME_INVALID,
                    CognitiveErrorCode.MSG_OUTCOME_INVALID, String.join("; ", problems));
        }

        String tenantId = resolveTenantId(req.getTenantId());
        String actor = resolveActor();
        String traceId = resolveTraceId(req.getTraceId());

        // ── 幂等第一段：同键重试直接回读既有记录（禁重复计算/重复建记录）──
        OutcomeEvaluationEntity replay =
                outcomeDao.findByIdempotencyKey(tenantId, trimToNull(req.getIdempotencyKey()));
        if (replay != null) {
            OutcomeEvaluateResult replayed = toResult(replay);
            replayed.setIdempotentReplay(true);
            replayed.setTraceId(traceId);
            log.info("W→D 评估幂等命中: evaluationId={} idempotencyKey={}", replay.getId(), replay.getIdempotencyKey());
            return replayed;
        }

        List<String> warnings = new ArrayList<>();

        // ── 步骤 2：对齐口径（先于误差计算，口径不齐算出来的误差没有意义）──
        String caliberAlignment = alignCaliber(req, warnings);

        // ── §9.4：七项防错误学习门禁（fail-loud）──
        OutcomeGuardEvaluator.Verdict verdict = guardEvaluator.evaluate(req);
        warnings.addAll(verdict.warnings());

        // ── 步骤 1 + 3 + 4：收集结果 → 计算误差 → 分解偏差 ──
        MetricComputation computed = computeMetrics(req, warnings);

        // ── 步骤 5：认知诊断（关联当时 Hypothesis / Belief 真库行）──
        List<OutcomeDiagnosis> diagnoses = diagnose(req, computed, verdict, warnings);

        // ── 步骤 6：生成更新建议（全部候选引用）──
        List<UpdateCandidateRef> candidates = buildCandidates(req, computed, verdict);

        // ── 步骤 7：影响分析（无数据源 ⇒ 如实登记，禁伪造）──
        warnings.add(IMPACT_ANALYSIS_UNAVAILABLE);

        // ── 步骤 8/9：回归门禁与审批发布不在本端点（由 §9.4 门禁 + security-engine 承载）──
        if (!verdict.blocked()) {
            warnings.add("AUTO_UPDATE_NOT_PERFORMED: guards passed but this endpoint never writes production "
                    + "Belief/Rule/Model; promotion requires baseline regression gate (step 8) and "
                    + "security-engine approval (step 9) on the candidate refs above");
        }

        // ── 状态判定（§9.4：命中任一门禁或有指标不适用 ⇒ 不得 EVALUATED）──
        OutcomeEvaluationStatus status = decideStatus(verdict, computed, caliberAlignment);

        // ── 步骤 10：闭环记录 ──
        OutcomeEvaluationEntity entity = toEntity(req, tenantId, actor, traceId, status,
                caliberAlignment, computed, diagnoses, candidates, verdict.blockers(), warnings);
        try {
            outcomeDao.insert(entity);
        } catch (DuplicateKeyException dup) {
            // 幂等第二段（并发兜底）：uk_cv274_outcome_idem 命中 → 回读同键记录
            OutcomeEvaluationEntity existing =
                    outcomeDao.findByIdempotencyKey(tenantId, entity.getIdempotencyKey());
            if (existing == null) {
                throw dup;
            }
            OutcomeEvaluateResult replayed = toResult(existing);
            replayed.setIdempotentReplay(true);
            replayed.setTraceId(traceId);
            return replayed;
        }

        OutcomeEvaluateResult result = toResult(entity);
        result.setTraceId(traceId);
        result.setDecompositions(computed.decompositions());

        publishOutcomeEvaluated(entity, result);
        observability.audit("cognitiveOutcome.evaluate", "outcome_evaluation",
                "evaluationId=" + entity.getId() + " scenarioId=" + entity.getScenarioId()
                        + " runId=" + nvl(entity.getRunId()) + " status=" + entity.getStatus()
                        + " metrics=" + result.getMetrics().size()
                        + " applicable=" + result.getApplicableSampleCount()
                        + " blockers=" + result.getBlockers().size()
                        + " candidates=" + result.getUpdateCandidates().size()
                        + " traceId=" + traceId);
        log.info("W→D 评估完成: evaluationId={} status={} metrics={}/{} blockers={}",
                entity.getId(), entity.getStatus(), result.getApplicableSampleCount(),
                result.getTotalSampleCount(), result.getBlockers().size());
        return result;
    }

    /** §9.2 步骤 10 闭环记录回查（单条）。 */
    public OutcomeEvaluateResult get(String evaluationId) {
        if (isBlank(evaluationId)) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_OUTCOME_INVALID,
                    CognitiveErrorCode.MSG_OUTCOME_INVALID, "evaluationId");
        }
        OutcomeEvaluationEntity row = outcomeDao.findById(evaluationId.trim());
        if (row == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E404_OUTCOME,
                    CognitiveErrorCode.MSG_OUTCOME_NOT_FOUND, "evaluationId=" + evaluationId);
        }
        return toResult(row);
    }

    /** 场景维评估历史（未删除，倒序，有界 200）。 */
    public List<OutcomeEvaluateResult> listByScenario(String scenarioId, String status) {
        if (isBlank(scenarioId)) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_OUTCOME_INVALID,
                    CognitiveErrorCode.MSG_OUTCOME_INVALID, "scenarioId");
        }
        List<OutcomeEvaluateResult> out = new ArrayList<>();
        for (OutcomeEvaluationEntity row : outcomeDao.listByScenario(resolveTenantId(null), scenarioId.trim(), status)) {
            out.add(toResult(row));
        }
        return out;
    }

    /** 运行维评估历史（§9.1「Run 成功 ≠ 验证过」的对照查询面）。 */
    public List<OutcomeEvaluateResult> listByRun(String runId) {
        if (isBlank(runId)) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_OUTCOME_INVALID,
                    CognitiveErrorCode.MSG_OUTCOME_INVALID, "runId");
        }
        List<OutcomeEvaluateResult> out = new ArrayList<>();
        for (OutcomeEvaluationEntity row : outcomeDao.listByRun(runId.trim())) {
            out.add(toResult(row));
        }
        return out;
    }

    // ═══════════════════════ 步骤 2：口径对齐 ═══════════════════════

    /**
     * 对齐口径（§9.2 步骤 2）。
     *
     * <p>本层只能核对 {@code caliberId} 是否同值；指标定义/时间范围/组织范围/币种单位四维明细
     * 比对属确定性计算服务职责，此处<b>不伪造</b>四维结论 —— 口径 ID 缺失即记
     * {@code UNCONFIRMED}，不一致即记 {@code MISMATCH} 并列出两侧值。</p>
     */
    private String alignCaliber(OutcomeEvaluateRequest req, List<String> warnings) {
        String forecast = trimToNull(req.getCaliberId());
        String actual = req.getActualRef() == null ? null : trimToNull(req.getActualRef().getCaliberId());
        if (forecast == null && actual == null) {
            warnings.add("CALIBER_UNCONFIRMED: neither forecast nor actual caliberId supplied");
            return CALIBER_UNCONFIRMED_PREFIX + "both-sides-missing";
        }
        if (forecast == null) {
            warnings.add("CALIBER_UNCONFIRMED: forecast caliberId missing (actual=" + actual + ")");
            return CALIBER_UNCONFIRMED_PREFIX + "forecast-missing";
        }
        if (actual == null) {
            warnings.add("CALIBER_UNCONFIRMED: actual caliberId missing (forecast=" + forecast + ")");
            return CALIBER_UNCONFIRMED_PREFIX + "actual-missing";
        }
        if (!forecast.equals(actual)) {
            warnings.add("CALIBER_MISMATCH: forecast=" + forecast + " actual=" + actual);
            return CALIBER_MISMATCH_PREFIX + "forecast=" + forecast + ",actual=" + actual;
        }
        return CALIBER_ALIGNED;
    }

    // ═══════════════════════ 步骤 1/3/4：数值 ═══════════════════════

    /** 一次评估的数值产物（逐指标 + 分解自检 + 计算引用回显；聚合值可由 metrics 确定性复算，不另存）。 */
    record MetricComputation(List<MetricEvaluation> metrics,
                             List<String> decompositions,
                             List<String> calculationRefs) {
    }

    private MetricComputation computeMetrics(OutcomeEvaluateRequest req, List<String> warnings) {
        List<String> metricRefs = distinctMetricRefs(req);
        Map<String, MetricCalculationBinding> bindings = indexBindings(req, metricRefs, warnings);

        OutcomeMetricValueReader reader = valueReaderProvider.getIfAvailable();
        Map<String, OutcomeMetricValueReader.ResolvedValue> resolved = new LinkedHashMap<>();
        List<String> calculationRefs = new ArrayList<>();
        collectCalculationRefs(bindings, calculationRefs);

        if (reader == null) {
            warnings.add(MISSING_VALUE_SEAM);
        } else {
            List<OutcomeMetricValueReader.MetricRefPair> pairs = new ArrayList<>();
            for (String metricRef : metricRefs) {
                MetricCalculationBinding b = bindings.get(metricRef);
                pairs.add(new OutcomeMetricValueReader.MetricRefPair(metricRef,
                        b == null ? null : b.getForecastRef(),
                        b == null ? null : b.getActualRef()));
            }
            OutcomeMetricValueReader.ResolveQuery query = new OutcomeMetricValueReader.ResolveQuery(
                    req.getScenarioId(), req.getRunId(), req.getForecastRunId(), req.getCaliberId(),
                    req.getEvaluationWindow().getFrom(), req.getEvaluationWindow().getTo(), pairs);
            for (OutcomeMetricValueReader.ResolvedValue rv : safeResolve(reader, query)) {
                if (rv != null && !isBlank(rv.metricRef())) {
                    resolved.put(rv.metricRef(), rv);
                }
            }
        }

        List<MetricEvaluation> metrics = new ArrayList<>();
        List<DeterministicErrorCalculator.MetricError> errors = new ArrayList<>();
        List<String> decompositions = new ArrayList<>();
        String caliber = trimToNull(req.getCaliberId());

        for (String metricRef : metricRefs) {
            OutcomeMetricValueReader.ResolvedValue rv = resolved.get(metricRef);
            DeterministicErrorCalculator.MetricError err;
            if (rv == null) {
                err = new DeterministicErrorCalculator.MetricError(metricRef, null, false, null, null,
                        reader == null ? "value seam absent" : "no deterministic value resolved for metricRef");
            } else {
                err = DeterministicErrorCalculator.compute(metricRef, rv.predicted(), rv.actual(),
                        rv.calculationRef());
                if (rv.unavailableReason() != null && !rv.unavailableReason().isBlank()
                        && (rv.predicted() == null || rv.actual() == null)) {
                    err = new DeterministicErrorCalculator.MetricError(metricRef, rv.calculationRef(), false,
                            err.absoluteError(), err.relativeError(), rv.unavailableReason());
                }
                checkDecomposition(metricRef, rv, decompositions, warnings);
            }
            errors.add(err);
            metrics.add(toMetricEvaluation(err, rv, caliber));
        }

        DeterministicErrorCalculator.Aggregate agg = DeterministicErrorCalculator.aggregate(errors);
        if (agg.mae() == null) {
            warnings.add("AGGREGATE_UNAVAILABLE: no applicable sample, MAE/MAPE omitted (never reported as 0)");
        }
        return new MetricComputation(metrics, decompositions, calculationRefs);
    }

    /** 步骤 4：偏差分解一致性自检（不平只登记残差，禁改贡献值凑平）。 */
    private void checkDecomposition(String metricRef,
                                    OutcomeMetricValueReader.ResolvedValue rv,
                                    List<String> decompositions,
                                    List<String> warnings) {
        if (rv.contributions() == null || rv.contributions().isEmpty()) {
            decompositions.add(metricRef + ": decomposition unavailable (no contribution members supplied)");
            return;
        }
        if (rv.predicted() == null || rv.actual() == null) {
            decompositions.add(metricRef + ": decomposition skipped (predicted/actual not both resolved)");
            return;
        }
        // 总差额口径与 DeterministicErrorCalculator 一致：预测 - 实际（带符号）
        BigDecimal totalDelta = rv.predicted().subtract(rv.actual());
        DeterministicErrorCalculator.Decomposition d = DeterministicErrorCalculator.decompose(
                totalDelta, rv.contributions(), DeterministicErrorCalculator.DEFAULT_TOLERANCE);
        if (d.consistent()) {
            decompositions.add(metricRef + ": decomposition consistent (sum=" + plain(d.sum())
                    + " total=" + plain(totalDelta) + " tolerance=" + plain(d.tolerance()) + ")");
            return;
        }
        String detail = metricRef + ": decomposition INCONSISTENT - " + d.reason();
        decompositions.add(detail);
        warnings.add(detail);
    }

    private List<OutcomeMetricValueReader.ResolvedValue> safeResolve(
            OutcomeMetricValueReader reader,
            OutcomeMetricValueReader.ResolveQuery query) {
        try {
            List<OutcomeMetricValueReader.ResolvedValue> values = reader.resolve(query);
            return values == null ? List.of() : values;
        } catch (RuntimeException e) {
            // 依赖故障如实透传（SUP-CTX-03 同族：禁吞异常返回伪空集合）
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN,
                    "OutcomeMetricValueReader.resolve failed: " + e.getClass().getSimpleName());
        }
    }

    private static void collectCalculationRefs(Map<String, MetricCalculationBinding> bindings,
                                               List<String> out) {
        Set<String> seen = new LinkedHashSet<>();
        for (MetricCalculationBinding b : bindings.values()) {
            addCalcRef(seen, out, b.getForecastRef());
            addCalcRef(seen, out, b.getActualRef());
        }
    }

    private static void addCalcRef(Set<String> seen, List<String> out, CalculationResultRef ref) {
        if (ref == null || isBlank(ref.getCalculationId())) {
            return;
        }
        if (seen.add(ref.getCalculationId())) {
            out.add(ref.getCalculationId());
        }
    }

    private static List<String> distinctMetricRefs(OutcomeEvaluateRequest req) {
        Set<String> refs = new LinkedHashSet<>();
        if (req.getMetricRefs() != null) {
            req.getMetricRefs().stream().filter(m -> !isBlank(m)).map(String::trim).forEach(refs::add);
        }
        if (req.getMetricCalculations() != null) {
            req.getMetricCalculations().stream()
                    .filter(b -> b != null && !isBlank(b.getMetricRef()))
                    .map(b -> b.getMetricRef().trim())
                    .forEach(refs::add);
        }
        return new ArrayList<>(refs);
    }

    /** 绑定索引；重复绑定与"绑定不在声明清单内"都如实登记（禁静默取后者覆盖前者）。 */
    private static Map<String, MetricCalculationBinding> indexBindings(OutcomeEvaluateRequest req,
                                                                      List<String> metricRefs,
                                                                      List<String> warnings) {
        Map<String, MetricCalculationBinding> map = new LinkedHashMap<>();
        if (req.getMetricCalculations() == null) {
            return map;
        }
        Set<String> declared = new LinkedHashSet<>(metricRefs);
        for (MetricCalculationBinding b : req.getMetricCalculations()) {
            if (b == null || isBlank(b.getMetricRef())) {
                continue;
            }
            String key = b.getMetricRef().trim();
            if (map.putIfAbsent(key, b) != null) {
                warnings.add("DUPLICATE_METRIC_BINDING: metricRef=" + key
                        + " bound more than once, first binding kept");
            }
        }
        for (String ref : declared) {
            if (!map.containsKey(ref)) {
                warnings.add("METRIC_BINDING_MISSING: metricRef=" + ref + " declared but not bound to any "
                        + "deterministic calculation product");
            }
        }
        return map;
    }

    private static MetricEvaluation toMetricEvaluation(DeterministicErrorCalculator.MetricError err,
                                                      OutcomeMetricValueReader.ResolvedValue rv,
                                                      String caliber) {
        MetricEvaluation m = new MetricEvaluation();
        m.setMetricRef(err.metricRef());
        m.setCalculationRef(err.calculationRef());
        m.setApplicable(err.applicable());
        m.setNotApplicableReason(err.notApplicableReason());
        m.setAbsoluteError(err.absoluteError());
        m.setRelativeError(err.relativeError());
        m.setCaliberId(caliber);
        if (rv != null) {
            m.setPredicted(rv.predicted());
            m.setActual(rv.actual());
            if (isBlank(m.getCalculationRef())) {
                m.setCalculationRef(rv.calculationRef());
            }
        }
        return m;
    }

    // ═══════════════════════ 步骤 5：认知诊断 ═══════════════════════

    /**
     * 关联当时使用的 Hypothesis / Belief（真库只读），形成解释与待验证假设。
     *
     * <p>诚实边界：本服务<b>不</b>产出 {@code SUPPORTED}/{@code REFUTED} 结论 ——
     * 那需要"假设断言 ↔ 指标方向 ↔ 阈值"的可判定映射，仓内无此数据源；
     * 一律 {@code INCONCLUSIVE} 并把关联到的资产 ID 作为待验证假设交出，
     * 由人工或后续裁决面定论（禁 LLM 自行裁决为事实，§13.1 422C 同族）。</p>
     */
    private List<OutcomeDiagnosis> diagnose(OutcomeEvaluateRequest req,
                                            MetricComputation computed,
                                            OutcomeGuardEvaluator.Verdict verdict,
                                            List<String> warnings) {
        List<OutcomeDiagnosis> out = new ArrayList<>();
        List<String> metricRefs = computed.metrics().stream().map(MetricEvaluation::getMetricRef).toList();
        Map<String, List<String>> hypothesesByMetric = loadHypotheses(metricRefs, warnings);
        Map<String, List<String>> beliefsByMetric = loadBeliefs(metricRefs, warnings);

        for (MetricEvaluation m : computed.metrics()) {
            OutcomeDiagnosis d = new OutcomeDiagnosis();
            d.setMetricRef(m.getMetricRef());
            d.setVerdict(OutcomeDiagnosis.VERDICT_INCONCLUSIVE);
            d.setExplanationBasis(false);
            List<String> assets = new ArrayList<>();
            assets.addAll(hypothesesByMetric.getOrDefault(m.getMetricRef(), List.of()));
            assets.addAll(beliefsByMetric.getOrDefault(m.getMetricRef(), List.of()));
            d.setAssetRefs(assets);
            d.setPendingHypotheses(new ArrayList<>(hypothesesByMetric.getOrDefault(m.getMetricRef(), List.of())));
            d.setExplanation(buildExplanation(m, verdict, assets));
            out.add(d);
        }
        warnings.add(DECISION_BASIS_UNLINKED);
        warnings.add(MODEL_VERSION_UNLINKED);
        return out;
    }

    /**
     * 诊断说明文本（<b>不含任何数值</b>）：只陈述"哪一维不可用、为什么不能定论"。
     * 数值归因一律走 {@code metrics[]} 的确定性字段，避免文本成为第二数值源（§0.3 红线 6）。
     */
    private static String buildExplanation(MetricEvaluation m,
                                           OutcomeGuardEvaluator.Verdict verdict,
                                           List<String> assets) {
        StringBuilder sb = new StringBuilder();
        if (!m.isApplicable()) {
            sb.append("metric not evaluable: ").append(nvl(m.getNotApplicableReason())).append(". ");
        }
        if (verdict.blocked()) {
            sb.append("auto-update blocked by guards: ").append(blockerNames(verdict.blockers())).append(". ");
        }
        if (assets.isEmpty()) {
            sb.append("no governed hypothesis/belief linked to this metric, verdict stays inconclusive.");
        } else {
            sb.append("linked governed assets listed as pending hypotheses, verdict stays inconclusive "
                    + "pending human adjudication.");
        }
        return sb.toString().trim();
    }

    private Map<String, List<String>> loadHypotheses(List<String> metricRefs, List<String> warnings) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        try {
            for (Map<String, Object> row : hypothesisDao.listByMetricRefs(metricRefs)) {
                String metricRef = str(row.get("metric_ref"));
                String id = str(row.get("id"));
                if (metricRef == null || id == null) {
                    continue;
                }
                map.computeIfAbsent(metricRef, k -> new ArrayList<>()).add("hyp:" + id);
            }
        } catch (RuntimeException e) {
            warnings.add("HYPOTHESIS_LINKAGE_UNAVAILABLE: " + e.getClass().getSimpleName()
                    + " (diagnosis proceeds without hypothesis refs, not with invented ones)");
        }
        return map;
    }

    private Map<String, List<String>> loadBeliefs(List<String> metricRefs, List<String> warnings) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        try {
            for (String metricRef : metricRefs) {
                for (Map<String, Object> row : beliefDao.listByVariable(metricRef)) {
                    String id = str(row.get("id"));
                    if (id != null) {
                        map.computeIfAbsent(metricRef, k -> new ArrayList<>()).add("bel:" + id);
                    }
                }
            }
        } catch (RuntimeException e) {
            warnings.add("BELIEF_LINKAGE_UNAVAILABLE: " + e.getClass().getSimpleName()
                    + " (diagnosis proceeds without belief refs, not with invented ones)");
        }
        return map;
    }

    // ═══════════════════════ 步骤 6：更新建议候选 ═══════════════════════

    /**
     * 生成更新建议（§9.2 步骤 6）。<b>全部是 Candidate/Proposal 引用</b>，本方法零写生产资产。
     *
     * <p>候选类型按可判定依据挑选，不猜业务意图：</p>
     * <ul>
     *   <li>有指标不适用（缺预测/缺实际）⇒ {@code UPDATE_EVIDENCE}（补数据面证据）；</li>
     *   <li>DQ 门禁失败 ⇒ {@code MODIFY_DQ_RULE}；</li>
     *   <li>关联到假设且误差可算 ⇒ {@code ADJUST_HYPOTHESIS}；</li>
     *   <li>回归未完成 ⇒ {@code REEVALUATE_MODEL}；</li>
     *   <li>证据不足/来源冲突 ⇒ {@code ADD_CASE}（补案例证据）。</li>
     * </ul>
     */
    private List<UpdateCandidateRef> buildCandidates(OutcomeEvaluateRequest req,
                                                    MetricComputation computed,
                                                    OutcomeGuardEvaluator.Verdict verdict) {
        List<UpdateCandidateRef> out = new ArrayList<>();
        List<String> notApplicable = computed.metrics().stream()
                .filter(m -> !m.isApplicable())
                .map(MetricEvaluation::getMetricRef)
                .toList();
        List<String> applicable = computed.metrics().stream()
                .filter(MetricEvaluation::isApplicable)
                .map(MetricEvaluation::getMetricRef)
                .toList();
        Set<OutcomeEvaluationBlocker> hit = new LinkedHashSet<>(verdict.blockers());

        if (!notApplicable.isEmpty()) {
            out.add(candidate(UpdateCandidateRef.TYPE_UPDATE_EVIDENCE,
                    "cand-outcome-evidence-" + shortId(), notApplicable));
        }
        if (hit.contains(OutcomeEvaluationBlocker.DQ_GATE_FAILED)) {
            out.add(candidate(UpdateCandidateRef.TYPE_MODIFY_DQ_RULE,
                    "cand-outcome-dq-" + shortId(), union(notApplicable, applicable)));
        }
        if (!applicable.isEmpty()) {
            out.add(candidate(UpdateCandidateRef.TYPE_ADJUST_HYPOTHESIS,
                    "cand-outcome-hypothesis-" + shortId(), applicable));
        }
        if (hit.contains(OutcomeEvaluationBlocker.BASELINE_REGRESSION_INCOMPLETE)) {
            out.add(candidate(UpdateCandidateRef.TYPE_REEVALUATE_MODEL,
                    "cand-outcome-model-" + shortId(), applicable));
        }
        if (hit.contains(OutcomeEvaluationBlocker.EVIDENCE_INSUFFICIENT_OR_CONFLICTED)) {
            out.add(candidate(UpdateCandidateRef.TYPE_ADD_CASE,
                    "cand-outcome-case-" + shortId(), union(notApplicable, applicable)));
        }
        for (UpdateCandidateRef c : out) {
            List<String> invalid = c.validate();
            if (!invalid.isEmpty()) {
                // 自检失败即内部缺陷 ⇒ fail-loud，禁把非法候选交出去
                throw new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                        CognitiveErrorCode.MSG_INTERNAL,
                        "malformed update candidate " + c.getCandidateType() + ": " + String.join(",", invalid));
            }
        }
        return out;
    }

    private static UpdateCandidateRef candidate(String type, String ref, List<String> basisMetricRefs) {
        UpdateCandidateRef c = new UpdateCandidateRef();
        c.setCandidateType(type);
        c.setCandidateRef(ref);
        // §7.4 同族候选状态机起点：候选刚生成，未过证据核对
        c.setStatus("GENERATED");
        c.setBasisMetricRefs(new ArrayList<>(basisMetricRefs));
        return c;
    }

    private static List<String> union(List<String> a, List<String> b) {
        Set<String> s = new LinkedHashSet<>(a);
        s.addAll(b);
        return new ArrayList<>(s);
    }

    // ═══════════════════════ 状态判定 ═══════════════════════

    /**
     * 状态判定（§9.4）：命中任一门禁 / 有指标不适用 / 口径未对齐 ⇒ {@code NEEDS_RECONCILE}；
     * 全部指标可算且口径对齐且门禁全过 ⇒ {@code EVALUATED}。
     *
     * <p>{@code PENDING} 只用于"记录已建但计算尚未发生"的中间态，本同步端点不产出；
     * {@code REJECTED} 属人工裁决态，本端点<b>不</b>自行判定（禁把"算不出"包装成"驳回"）。</p>
     */
    private OutcomeEvaluationStatus decideStatus(OutcomeGuardEvaluator.Verdict verdict,
                                                MetricComputation computed,
                                                String caliberAlignment) {
        if (verdict.blocked()) {
            return OutcomeEvaluationStatus.NEEDS_RECONCILE;
        }
        if (!CALIBER_ALIGNED.equals(caliberAlignment)) {
            return OutcomeEvaluationStatus.NEEDS_RECONCILE;
        }
        boolean allApplicable = computed.metrics().stream().allMatch(MetricEvaluation::isApplicable);
        if (!allApplicable || computed.metrics().isEmpty()) {
            return OutcomeEvaluationStatus.NEEDS_RECONCILE;
        }
        boolean decompositionOk = computed.decompositions().stream()
                .noneMatch(s -> s.contains("INCONSISTENT"));
        return decompositionOk ? OutcomeEvaluationStatus.EVALUATED : OutcomeEvaluationStatus.NEEDS_RECONCILE;
    }

    // ═══════════════════════ 落库 / 回读 映射 ═══════════════════════

    private OutcomeEvaluationEntity toEntity(OutcomeEvaluateRequest req, String tenantId, String actor,
                                            String traceId, OutcomeEvaluationStatus status,
                                            String caliberAlignment, MetricComputation computed,
                                            List<OutcomeDiagnosis> diagnoses,
                                            List<UpdateCandidateRef> candidates,
                                            List<OutcomeEvaluationBlocker> blockers,
                                            List<String> warnings) {
        OutcomeEvaluationEntity e = new OutcomeEvaluationEntity();
        e.setId(UUID.randomUUID().toString());
        e.setTenantId(tenantId);
        e.setOrgId(trimToNull(req.getOrgId()));
        e.setScenarioId(req.getScenarioId().trim());
        e.setBlueprintId(trimToNull(req.getBlueprintId()));
        e.setBlueprintVersion(trimToNull(req.getBlueprintVersion()));
        e.setRunId(trimToNull(req.getRunId()));
        e.setForecastRunId(trimToNull(req.getForecastRunId()));
        e.setActualAssetId(trimToNull(req.getActualRef().getAssetId()));
        e.setActualVersion(trimToNull(req.getActualRef().getVersion()));
        e.setCaliberId(trimToNull(req.getCaliberId()));
        e.setCaliberAlignment(caliberAlignment);
        e.setWindowFromDate(req.getEvaluationWindow().fromDate());
        e.setWindowToDate(req.getEvaluationWindow().toDate());
        e.setStatus(status.name());
        e.setMetricResultsJson(writeJson(metricRows(computed), "[]"));
        e.setDiagnosesJson(writeJson(diagnoses, "[]"));
        e.setUpdateCandidatesJson(writeJson(candidates, "[]"));
        e.setWarningsJson(writeJson(warnings, "[]"));
        e.setBlockersJson(writeJson(blockers.stream().map(Enum::name).toList(), "[]"));
        e.setCalculationRefsJson(writeJson(computed.calculationRefs(), "[]"));
        e.setTraceId(traceId);
        e.setIdempotencyKey(trimToNull(req.getIdempotencyKey()));
        e.setVersionNo("v1");
        e.setCreateBy(actor);
        e.setUpdateBy(actor);
        return e;
    }

    /** 落库行 = §9.3 metrics[] 项 + 聚合摘要（数值全部来自确定性计算，禁在此重算）。 */
    private static List<Map<String, Object>> metricRows(MetricComputation computed) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MetricEvaluation m : computed.metrics()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("metricRef", m.getMetricRef());
            row.put("predicted", m.getPredicted());
            row.put("actual", m.getActual());
            row.put("absoluteError", m.getAbsoluteError());
            row.put("relativeError", m.getRelativeError());
            row.put("calculationRef", m.getCalculationRef());
            row.put("applicable", m.isApplicable());
            row.put("notApplicableReason", m.getNotApplicableReason());
            row.put("caliberId", m.getCaliberId());
            rows.add(row);
        }
        return rows;
    }

    private OutcomeEvaluateResult toResult(OutcomeEvaluationEntity e) {
        OutcomeEvaluateResult r = new OutcomeEvaluateResult();
        r.setEvaluationId(e.getId());
        r.setStatus(e.getStatus());
        r.setCaliberAlignment(e.getCaliberAlignment());
        r.setTraceId(e.getTraceId());
        r.setMetrics(readMetrics(e.getMetricResultsJson()));
        r.setDiagnoses(readJson(e.getDiagnosesJson(), new TypeReference<List<OutcomeDiagnosis>>() { },
                List.of(), "diagnoses"));
        r.setUpdateCandidates(readJson(e.getUpdateCandidatesJson(),
                new TypeReference<List<UpdateCandidateRef>>() { }, List.of(), "updateCandidates"));
        r.setWarnings(readJson(e.getWarningsJson(), LIST_OF_STRING, List.of(), "warnings"));
        r.setCalculationRefs(readJson(e.getCalculationRefsJson(), LIST_OF_STRING, List.of(), "calculationRefs"));
        r.setBlockers(readBlockers(e.getBlockersJson()));
        // 聚合值不落库（可由 metrics[] 确定性复算，避免存两份可能漂移的数值）
        DeterministicErrorCalculator.Aggregate agg = DeterministicErrorCalculator.aggregate(
                r.getMetrics().stream()
                        .map(m -> new DeterministicErrorCalculator.MetricError(m.getMetricRef(),
                                m.getCalculationRef(), m.isApplicable(), m.getAbsoluteError(),
                                m.getRelativeError(), m.getNotApplicableReason()))
                        .toList());
        r.setMae(agg.mae());
        r.setMape(agg.mape());
        r.setTotalSampleCount(agg.sampleCount());
        r.setApplicableSampleCount(agg.maeSampleCount());
        return r;
    }

    private List<MetricEvaluation> readMetrics(String json) {
        List<Map<String, Object>> rows = readJson(json, LIST_OF_MAP, List.of(), "metricResults");
        List<MetricEvaluation> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            MetricEvaluation m = new MetricEvaluation();
            m.setMetricRef(str(row.get("metricRef")));
            m.setPredicted(decimal(row.get("predicted")));
            m.setActual(decimal(row.get("actual")));
            m.setAbsoluteError(decimal(row.get("absoluteError")));
            m.setRelativeError(decimal(row.get("relativeError")));
            m.setCalculationRef(str(row.get("calculationRef")));
            m.setCaliberId(str(row.get("caliberId")));
            m.setNotApplicableReason(str(row.get("notApplicableReason")));
            Object applicable = row.get("applicable");
            m.setApplicable(applicable == null || Boolean.parseBoolean(String.valueOf(applicable)));
            out.add(m);
        }
        return out;
    }

    private static List<OutcomeEvaluationBlocker> readBlockers(String json) {
        List<OutcomeEvaluationBlocker> out = new ArrayList<>();
        for (String name : readJson(json, LIST_OF_STRING, List.of(), "blockers")) {
            try {
                out.add(OutcomeEvaluationBlocker.valueOf(name));
            } catch (IllegalArgumentException e) {
                // 库内出现词表外的值 = 数据缺陷，如实保留为告警而不是静默丢弃
                log.warn("blockers_text 含词表外值，已忽略但登记 WARN: {}", name);
            }
        }
        return out;
    }

    private static <T> T readJson(String json, TypeReference<T> type, T fallback, String what) {
        if (isBlank(json)) {
            return fallback;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            // 读侧不 fail-closed（历史记录损坏不应让整个查询面 500），但必须显式告警
            log.error("评估记录 {} 列反序列化失败，按空值返回并告警: {}", what, e.getOriginalMessage());
            return fallback;
        }
    }

    /** 写侧 fail-loud：序列化失败即内部缺陷，禁写 "{}"/"[]" 掩盖数据丢失。 */
    private static String writeJson(Object value, String emptyFallback) {
        if (value == null) {
            return emptyFallback;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL,
                    CognitiveErrorCode.MSG_INTERNAL,
                    "outcome evaluation payload not serializable: " + e.getOriginalMessage());
        }
    }

    // ═══════════════════════ 事件 / 上下文 ═══════════════════════

    /** §13 事件：载荷只带 ID / 状态 / 引用串与 traceId，禁带敏感正文与数值明细。 */
    private void publishOutcomeEvaluated(OutcomeEvaluationEntity e, OutcomeEvaluateResult r) {
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            log.warn("OutcomeEvaluated 事件未发布：EventBusService 未装配（评估记录已落库，降级不阻塞）");
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", EventTypes.Cognition.OUTCOME_EVALUATED);
        payload.put("evaluationId", e.getId());
        payload.put("tenantId", e.getTenantId());
        payload.put("scenarioId", e.getScenarioId());
        payload.put("blueprintId", e.getBlueprintId());
        payload.put("blueprintVersion", e.getBlueprintVersion());
        payload.put("runId", e.getRunId());
        payload.put("forecastRunId", e.getForecastRunId());
        payload.put("actualAssetId", e.getActualAssetId());
        payload.put("status", e.getStatus());
        payload.put("blockers", r.getBlockers().stream().map(Enum::name).toList());
        payload.put("updateCandidateRefs", r.getUpdateCandidates().stream()
                .map(UpdateCandidateRef::getCandidateRef).toList());
        payload.put("calculationRefs", r.getCalculationRefs());
        payload.put("traceId", e.getTraceId());
        try {
            bus.publish(KafkaTopics.topicForAggregate("cognition"), payload);
        } catch (RuntimeException ex) {
            log.warn("OutcomeEvaluated 事件发布失败（评估记录已落库，降级不阻塞）: err={}", ex.getMessage());
        }
    }

    private static String resolveTenantId(String requested) {
        if (!isBlank(requested)) {
            return requested.trim();
        }
        try {
            UserContext ctx = UserContext.getCurrent();
            if (ctx != null && !isBlank(ctx.getTenantId())) {
                return ctx.getTenantId();
            }
        } catch (RuntimeException ignored) {
            // 无登录上下文（内部调用/测试）→ default
        }
        return "default";
    }

    private static String resolveActor() {
        try {
            String name = UserContext.getCurrentUsername();
            if (!isBlank(name)) {
                return name.trim();
            }
        } catch (RuntimeException ignored) {
            // 无登录上下文 → system
        }
        return "system";
    }

    private static String resolveTraceId(String requested) {
        if (!isBlank(requested)) {
            return requested.trim();
        }
        String mdc = MDC.get(EcosTraceContext.MDC_KEY_TRACE_ID);
        return isBlank(mdc) ? UUID.randomUUID().toString() : mdc;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String blockerNames(List<OutcomeEvaluationBlocker> blockers) {
        return blockers.isEmpty() ? "-" : blockers.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("-");
    }

    private static String plain(BigDecimal v) {
        return v == null ? "-" : v.toPlainString();
    }

    private static BigDecimal decimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal b) {
            return b;
        }
        if (raw instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equals(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            log.warn("评估记录数值列不可解析为 BigDecimal，按 null 返回并告警: {}", s);
            return null;
        }
    }

    private static String str(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private static String trimToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static String nvl(String s) {
        return isBlank(s) ? "-" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
