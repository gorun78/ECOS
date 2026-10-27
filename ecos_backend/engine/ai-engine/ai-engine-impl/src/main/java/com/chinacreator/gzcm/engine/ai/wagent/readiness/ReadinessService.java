package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.DegradeCode;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessCheckStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessGrade;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessLayer;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentMissionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessItemDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentMissionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessGapDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessItemDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentQuestionService;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.EngineProbeClient.LayerProbe;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.ReadinessCheckCode.Judgement;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Readiness 评估编排服务（详细设计-10 F10-08 算法树的实位；§4.1 组件落位
 * {@code readiness/ReadinessService}；差距分析 P0-8「四层探测 + 阈值单源 + 评估落库零实现」的收口点）。
 *
 * <p><b>一条主干</b>：{@link #evaluate} = 并行四层探测（{@link EngineProbeClient}）→ 档位归一
 * （{@link ThresholdPolicy#profileForIntent}）→ 逐 {@link ReadinessCheckCode} 取度量 + 阈值判档 →
 * {@link ReadinessGrader} 定级 → 反事实解锁档 → <b>先写 V231 明细 + V232 缺口，再写 V230 头，
 * 最后替代旧评估</b>。三段产物同源同刻落库，事后任一档位都可由
 * {@code measured_json}/{@code threshold_json}/{@code layer_summary_json} 复盘。</p>
 *
 * <h3>F10-08 五条硬规则在本类的落点</h3>
 * <ol>
 *   <li>①「grade/status 服务端确定性计算，LLM 仅可填 description/impact」⇒ 本类
 *       <b>不调 LLM</b>，因此 V232 的 description/impact 也是拼装文本（{@code ReadinessGapService}），
 *       全链无生成式内容进入定级面；</li>
 *   <li>②「探测异常 ⇒ UNKNOWN，禁 fail-open」⇒ 三种"判不了"（度量缺席 / 阈值片段缺席 /
 *       判档自体抛错）全部折算 {@link Judgement#unknown}，且 {@code probe_reason} 留痕；</li>
 *   <li>③「LLM 文本禁金额」⇒ 不适用（无 LLM）；数值只以字符串进 {@code measured_json}
 *       （{@link CheckOutcome#measuredSnapshot()}，铁律禁 double 漂移）；</li>
 *   <li>④「评估只追加 + {@code superseded_by}」⇒ 本类<b>从不 update 旧评估行的内容</b>，
 *       只经 {@link WAgentReadinessDao#markSuperseded} 写指针，pinned 行由该语句的
 *       {@code is_pinned = 0} 闸自然豁免（F10-07 硬规则④后半句）；</li>
 *   <li>⑤「{@code refill_round ≤ 3}，超限输出缺口并转 {@code awaiting_input}」⇒
 *       {@link #refill} 的超限分支不报错、不派发、不复评，200 出缺口清单并挂起问题。</li>
 * </ol>
 *
 * <h3>落库事务顺序（裁决 D-B2④-15）</h3>
 * <p>wagent 包无 {@code @Transactional} 惯例（既有 {@code StepExecutor} 同形），故用
 * <b>顺序</b>代替注解：明细（V231/V232）→ 头（V230）→ 替代位。中途崩溃只可能留下
 * <b>无主的孤儿明细行</b>（读面按 {@code assessment_id} 取，无头行即不可见，不是错误结论），
 * 绝不会留下"有头无明细"的假评估——后者会被 #12 端点当当前等级读出去，是假绿方向。</p>
 *
 * <h3>剩余差距（诚实登记，不在本类内掩盖）</h3>
 * <p><b>#146b 订正（原文「七项恒 absent」不再成立者已撤）</b>：八度量现全部有供数面——
 * 本域证 {@code GoalCaliberMetricSource}（onto.caliber_defined）+ 四枚属主 REST 取数源
 * （{@code DataFreshnessMetricSource} data.staleness_days / {@code CaliberConflictCountMetricSource}
 * onto.conflict_count / {@code KbProfileStockMetricSource} know.sample_size /
 * {@code CognitiveModelReadinessMetricSource} cog.cm_ready+backtest_ready，属主端点
 * 分别落 data/ontology/kb/cognitive 四引擎，经 {@code ReadinessMetricFetchClient}
 * 透传凭证出向）。<b>仍然恒 absent 的三项（口径裁决，非缺端点）</b>：
 * ① {@code data.check_coverage}——"目标口径覆盖的期间/组织单元比例"无属主读端点且
 * 无本域可推口径（比例没有分母定义），取不到就是取不到，端点补齐后加源即可；
 * ② {@code know.applicability}——"命中画像对当前 scope 的适用度"需要画像 scope 匹配
 * 语义，kb 库存量端点（/stock 两计）不承载该语义，禁从样本量反推适用度；
 * ③ 未映射意图（DIAGNOSE/RANK/MONITOR/BUILD/REVIEW）的 C 层两项——契约
 * {@code CognitiveModelType.fromLegacyModelType} 只有四枚映射，禁就近猜型。
 * 另两项已知盲区如实保留：异步腿无凭证载体 ⇒ 取数 401 ⇒ absent（有意 fail-closed，
 * 服务间凭证签名腿另批）；{@code onto.caliber_defined} 只答"绑没绑"不答"版本侧是否
 * 仍 PUBLISHED"（属主口径版本状态读端点缺席，known gap (b) 不改）。</p>
 */
@Service
public class ReadinessService {

    private static final Logger log = LoggerFactory.getLogger(ReadinessService.class);

    /** 反事实解锁档至少改进一档才写入 {@code unlock_to}（同档＝无解锁效应 ⇒ 留 NULL）。 */
    private static final String REASON_THRESHOLD_MISSING = "threshold-fragments-missing:";
    private static final String REASON_METRIC_NOT_WIRED = "metric-source-not-wired:";
    private static final String REASON_JUDGE_FAILED = "judge-failed:";

    private final EngineProbeClient probeClient;
    private final ThresholdPolicy thresholdPolicy;
    private final ObjectProvider<ReadinessMetricSource> metricSources;
    private final WAgentReadinessDao readinessDao;
    private final WAgentReadinessItemDao itemDao;
    private final WAgentMissionDao missionDao;
    private final ReadinessGapService gapService;
    private final WAgentQuestionService questionService;
    /** 定级器是无状态纯函数，非 Spring bean（§4.1 组件表口径）。 */
    private final ReadinessGrader grader = new ReadinessGrader();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReadinessService(EngineProbeClient probeClient,
                           ThresholdPolicy thresholdPolicy,
                           ObjectProvider<ReadinessMetricSource> metricSources,
                           WAgentReadinessDao readinessDao,
                           WAgentReadinessItemDao itemDao,
                           WAgentMissionDao missionDao,
                           ReadinessGapService gapService,
                           WAgentQuestionService questionService) {
        this.probeClient = probeClient;
        this.thresholdPolicy = thresholdPolicy;
        this.metricSources = metricSources;
        this.readinessDao = readinessDao;
        this.itemDao = itemDao;
        this.missionDao = missionDao;
        this.gapService = gapService;
        this.questionService = questionService;
    }

    /**
     * 一次评估的产物。
     *
     * @param assessmentId 新 V230 头行 ID
     * @param assessment   头行（已落库形态）
     * @param grade        定级结果（含命中规则，供响应面/复盘）
     * @param outcomes     逐项一手结论（V231 全量明细的内存形态）
     * @param gaps         本次落库的缺口行（V232）
     */
    public record EvaluationResult(String assessmentId, WAgentReadinessDO assessment,
                                   ReadinessGradeResult grade, List<CheckOutcome> outcomes,
                                   List<WAgentReadinessGapDO> gaps) {

        public EvaluationResult {
            outcomes = Collections.unmodifiableList(new ArrayList<>(outcomes));
            gaps = Collections.unmodifiableList(new ArrayList<>(gaps));
        }
    }

    /**
     * 补齐复评的产物（§5.2 #13 响应面：{@code refillRound}/{@code newGrade}/{@code taskIds}）。
     *
     * @param awaitingInput 超限标志（{@code true} ⇒ 本轮未派发未复评，问题已转 awaiting_input）
     */
    public record RefillResult(String assessmentId, String previousGrade, String newGrade,
                               int refillRound, int maxRefillRounds, boolean awaitingInput,
                               List<String> taskIds, List<Map<String, Object>> deferred,
                               List<WAgentReadinessGapDO> openGaps) {
    }

    // ══════════════════════════ 评估主干 ══════════════════════════

    /**
     * 以问题为锚做评估：反查 Goal（I 层本域度量的取证锚点）、按意图定档、现探四层。
     *
     * <p>{@code runId} 可为 null（预评/手工触发），但 {@link #refill} 必须带 Run——轮次台账
     * 单源在 Run 的 {@code consumption_json}（F10-11）。</p>
     */
    public EvaluationResult evaluateForQuestion(WAgentQuestionDO question, String runId, String operator) {
        if (question == null) {
            throw WAgentOrchestrationException.validation("问题行缺失，无法评估（无 question_id 即无 V230 NOT NULL 锚）");
        }
        return evaluate(contextFor(question, runId), operator);
    }

    /** 问题 → 评估上下文（mission→goal 反查链；任一环取不到即留 null，由取数面记 absent）。 */
    public ReadinessProbeContext contextFor(WAgentQuestionDO question, String runId) {
        String goalId = resolveGoalId(question.getMissionId());
        return new ReadinessProbeContext(question.getId(), question.getMissionId(), goalId, runId,
                null, question.getIntent(), question.getTenantId(), question.getOrgId(), Map.of());
    }

    /**
     * 评估主干（F10-08 算法树）。探测结论一律现取，禁复用调用方传入的旧 {@code probes}
     * （{@link ReadinessProbeContext#withProbes} 覆盖之）。
     */
    public EvaluationResult evaluate(ReadinessProbeContext base, String operator) {
        if (base == null || isBlank(base.questionId())) {
            throw WAgentOrchestrationException.validation("评估上下文缺 question_id（V230 NOT NULL）");
        }
        if (isBlank(base.tenantId())) {
            // V230/V231/V232 三表 tenant_id 均 NOT NULL；无租户上下文即拒，禁写"看起来对"的全局评估
            throw WAgentOrchestrationException.validation(
                    "评估上下文缺 tenant_id（三表 tenant_id NOT NULL，无租户即拒——禁无主评估落库）");
        }
        ReadinessProbeContext ctx = base.withProbes(probeClient.probeAll());
        String profile = ThresholdPolicy.normalize(
                isBlank(base.profileId()) ? ThresholdPolicy.profileForIntent(base.intent()) : base.profileId());

        // 旧当前评估先行定位（头行插入后 findCurrent 的平局序不可靠，替代位必须指向"本次之前的那一行"）
        String previousAssessmentId = readinessDao.findCurrentByQuestionId(ctx.questionId())
                .filter(a -> ctx.tenantId().equals(a.getTenantId()))
                .map(WAgentReadinessDO::getId)
                .orElse(null);

        List<CheckOutcome> outcomes = new ArrayList<>();
        Map<String, String> sourceByCheckCode = new LinkedHashMap<>();
        for (ReadinessCheckCode code : ReadinessCheckCode.values()) {
            ProbeResult probed = probeOne(code, ctx, profile);
            outcomes.add(probed.outcome());
            sourceByCheckCode.put(code.checkCode(), probed.sourceId());
        }
        ReadinessGradeResult grade = grader.grade(outcomes.stream().map(CheckOutcome::toCheck).toList());
        Map<String, String> unlockTo = counterfactualUnlock(outcomes, grade);

        String assessmentId = UUID.randomUUID().toString();
        // ── 顺序即事务（D-B2④-15）：明细 → 缺口 → 头 → 替代位 ──
        for (CheckOutcome outcome : outcomes) {
            itemDao.insert(newItem(assessmentId, ctx, outcome,
                    sourceByCheckCode.get(outcome.code().checkCode()), operator));
        }
        List<WAgentReadinessGapDO> gaps = gapService.openGaps(assessmentId, ctx, outcomes,
                grade.grade().value(), unlockTo, operator);
        WAgentReadinessDO head = newHead(assessmentId, ctx, profile, grade, outcomes.size(), operator);
        readinessDao.insert(head);
        supersedePrevious(previousAssessmentId, assessmentId, operator);

        log.info("[F10-08] Readiness 评估落库: assessmentId={} questionId={} grade={} rule={} 缺项={} 缺口={}",
                assessmentId, ctx.questionId(), grade.grade().value(), grade.matchedRule(),
                grade.missingItems().size(), gaps.size());
        return new EvaluationResult(assessmentId, head, grade, outcomes, gaps);
    }

    // ══════════════════════════ 补齐复评（F10-09） ══════════════════════════

    /**
     * 缺口补齐一轮（§5.2 #13，循环 ≤3）。
     *
     * <p>超限分支（硬规则⑤）：<b>HTTP 200 + 问题转 awaiting_input + 当前缺口清单</b>，
     * 不派发、不复评、不计轮次——把超限写成 4xx/5xx 会让调用方以为"重试就能过"，
     * 而超限的正确语义是"这里需要人"（裁决 D-B2④-3）。</p>
     *
     * <p>未超限：<b>先预留轮次</b>再派发再复评（裁决 D-B2④-13：轮次每次调用即计，
     * 否则"复评失败就不计数"可被无限重试绕过 ≤3 上限，比放开循环更隐蔽）。</p>
     */
    public RefillResult refill(WAgentQuestionDO question, WAgentReadinessDO currentAssessment,
                               List<String> gapIds, String actionType, String operator) {
        ReadinessGapService.RefillLedger ledger = gapService.loadRefillLedger(currentAssessment, question);
        ReadinessProbeContext ctx = contextFor(question, ledger.run().getId());
        if (ledger.limitReached()) {
            boolean suspended = questionService.markAwaitingInput(question.getId(), question.getTenantId(), operator);
            log.warn("[F10-09] 补评轮次越限: questionId={} refill_rounds={}/{} awaiting_input={}",
                    question.getId(), ledger.refillRound(), ledger.maxRefillRounds(), suspended);
            return new RefillResult(currentAssessment.getId(), currentAssessment.getGrade(),
                    currentAssessment.getGrade(), ledger.refillRound(), ledger.maxRefillRounds(), true,
                    List.of(), List.of(), gapService.findOpenGaps(currentAssessment.getId()));
        }
        List<WAgentReadinessGapDO> selected = selectGaps(currentAssessment, gapIds);
        // 轮次先预留（D-B2④-13）：复评失败也计一轮，否则 ≤3 可被"失败就不计数"的重试绕过
        int roundConsumed = gapService.recordRefillRound(ledger, null, operator).refillRounds();
        ReadinessGapService.TaskDispatch dispatch =
                gapService.dispatchResolutionTasks(selected, actionType, ctx, operator);
        EvaluationResult fresh = evaluate(ctx, operator);
        gapService.attachAssessmentToRun(ledger.run(), fresh.assessmentId(), operator);
        String previousGrade = currentAssessment.getGrade();
        return new RefillResult(fresh.assessmentId(), previousGrade, fresh.grade().grade().value(),
                roundConsumed, ledger.maxRefillRounds(), false,
                dispatch.taskIds(), dispatch.deferred(), gapService.findOpenGaps(fresh.assessmentId()));
    }

    /**
     * 派发对象筛选：空 {@code gapIds} ⇒ 本次评估全部 open 缺口；显式指定的缺口必须属于
     * 本评估且仍 open，否则同掩 404（{@code GapTaskTest} 依赖的防枚举口径，§5.3）。
     */
    private List<WAgentReadinessGapDO> selectGaps(WAgentReadinessDO assessment, List<String> gapIds) {
        List<WAgentReadinessGapDO> open = gapService.findOpenGaps(assessment.getId());
        if (gapIds == null || gapIds.isEmpty()) {
            return open;
        }
        Map<String, WAgentReadinessGapDO> byId = new LinkedHashMap<>();
        for (WAgentReadinessGapDO gap : open) {
            byId.put(gap.getId(), gap);
        }
        List<WAgentReadinessGapDO> picked = new ArrayList<>();
        for (String gapId : gapIds) {
            WAgentReadinessGapDO gap = gapId == null ? null : byId.get(gapId.trim());
            if (gap == null) {
                // 跨评估/已处置/伪造 ID 一律同掩 404（禁以 403/400 区分，防缺口 ID 枚举）
                throw WAgentOrchestrationException.notFound();
            }
            picked.add(gap);
        }
        return picked;
    }

    // ══════════════════════════ 探测与判档 ══════════════════════════

    /** 单项探测产物：结论 + 供数面标识（供数面名要能落进 measured_json，禁无痕度量）。 */
    private record ProbeResult(CheckOutcome outcome, String sourceId) {
    }

    private ProbeResult probeOne(ReadinessCheckCode code, ReadinessProbeContext ctx, String profile) {
        ThresholdSet thresholds = thresholdPolicy.resolve(profile, code);
        List<String> missingFragments = thresholds.missing(code.thresholdFragments());
        String probeReason = probeReasonOf(code.layer(), ctx);

        MetricWithSource picked = pickMetric(code, ctx);
        Judgement judgement;
        if (!thresholds.hasAll(code.thresholdFragments())) {
            // 前置闸：片段不齐绝不能进 judge——judge 内 value(frag).orElseThrow() 会炸
            judgement = Judgement.unknown(REASON_THRESHOLD_MISSING + String.join(",", missingFragments));
        } else {
            judgement = judgeSafely(code, picked.value(), thresholds);
        }
        CheckOutcome outcome = new CheckOutcome(code, picked.value(), thresholds, judgement,
                missingFragments, probeReason);
        return new ProbeResult(outcome, picked.sourceId());
    }

    /** 硬规则②的最后一道：判档自体抛任何运行时异常都折成 UNKNOWN（禁让"算不出"冒充实测档位）。 */
    private Judgement judgeSafely(ReadinessCheckCode code, MetricValue metric, ThresholdSet thresholds) {
        try {
            Judgement judged = code.judge(metric, thresholds);
            return judged == null ? Judgement.unknown("judge-returned-null") : judged;
        } catch (RuntimeException unexpected) {
            log.warn("[F10-08] 判档异常 ⇒ UNKNOWN: check_code={} err={}",
                    code.checkCode(), String.valueOf(unexpected));
            return Judgement.unknown(REASON_JUDGE_FAILED + unexpected.getClass().getSimpleName());
        }
    }

    /** 层可达性只作证据透传，不作取数屏蔽（裁决 D-B2④-12：本域事实不因跨层探测失败而消失）。 */
    private static String probeReasonOf(ReadinessLayer layer, ReadinessProbeContext ctx) {
        LayerProbe probe = ctx.probe(layer);
        if (probe == null) {
            return "layer-not-probed";
        }
        if (probe.reachable()) {
            return null;
        }
        return isBlank(probe.reason()) ? "layer-unreachable" : probe.reason();
    }

    /** 取数面选择：首个 supports 者供数；无源 ⇒ absent 并点名"未接线"（禁造数）。 */
    private MetricWithSource pickMetric(ReadinessCheckCode code, ReadinessProbeContext ctx) {
        List<ReadinessMetricSource> available = metricSources.stream().toList();
        for (ReadinessMetricSource source : available) {
            if (source == null || !source.supports(code)) {
                continue;
            }
            try {
                MetricValue value = source.measure(code, ctx);
                return new MetricWithSource(value == null
                        ? MetricValue.absent("metric-source-returned-null:" + source.sourceId()) : value,
                        source.sourceId());
            } catch (RuntimeException sourceFailed) {
                // 取数故障 ⇒ absent（检查项随后 UNKNOWN），异常不外溢（一次评估不能被一个取数面带崩）
                log.warn("[F10-08] 度量取数失败 ⇒ absent: check_code={} source={} err={}",
                        code.checkCode(), source.sourceId(), String.valueOf(sourceFailed));
                return new MetricWithSource(MetricValue.absent("metric-source-failed:" + source.sourceId()),
                        source.sourceId());
            }
        }
        return new MetricWithSource(MetricValue.absent(metricNotWiredReason(code)), null);
    }

    private record MetricWithSource(MetricValue value, String sourceId) {
    }

    // ══════════════════════════ 反事实解锁档 ══════════════════════════

    /**
     * 逐缺项重算"该项补齐后可到的档位"（F10-09 {@code unlock_value} 的可计算部分）。
     *
     * <p>口径（裁决 D-B2④-11 订正）：<b>补上＝该项变 pass</b>，不是"该项从检查集里消失"——
     * 消失会让 {@link ReadinessGrader} 的"该层无检查项 ⇒ UNKNOWN"把它重新算成更差的档，
     * 得出"补了反而降档"的荒谬结论。同档 ⇒ {@code null}（无解锁效应，禁写成 "C -&gt; C" 冒充进展）。</p>
     */
    Map<String, String> counterfactualUnlock(List<CheckOutcome> outcomes, ReadinessGradeResult current) {
        Map<String, String> unlockTo = new LinkedHashMap<>();
        for (CheckOutcome target : outcomes) {
            if (!target.missing()) {
                continue;
            }
            List<ReadinessCheck> swapped = new ArrayList<>(outcomes.size());
            for (CheckOutcome other : outcomes) {
                swapped.add(other.code() == target.code()
                        ? ReadinessCheck.of(other.code().layer(), other.code().checkCode(),
                                ReadinessCheckStatus.PASS)
                        : other.toCheck());
            }
            ReadinessGradeResult after = grader.grade(swapped);
            unlockTo.put(target.code().checkCode(),
                    after.grade().ordinal() < current.grade().ordinal() ? after.grade().value() : null);
        }
        return Collections.unmodifiableMap(unlockTo);
    }

    // ══════════════════════════ 落库行装配 ══════════════════════════

    private WAgentReadinessItemDO newItem(String assessmentId, ReadinessProbeContext ctx,
                                          CheckOutcome outcome, String sourceId, String operator) {
        Map<String, Object> measured = new LinkedHashMap<>(outcome.measuredSnapshot());
        // 供数面标识落列：无源时写 "none"，与 absent reason 里的 check_code 点名互为佐证（禁无痕度量）
        measured.put("metric_source", sourceId == null ? "none" : sourceId);
        measured.put("engine_key", outcome.code().engineKey().name());
        WAgentReadinessItemDO item = new WAgentReadinessItemDO();
        item.setId(UUID.randomUUID().toString());
        item.setAssessmentId(assessmentId);
        item.setLayer(outcome.code().layer().value());
        item.setCheckCode(outcome.code().checkCode());
        item.setStatus(outcome.status().value());
        item.setMeasuredJson(writeJson(measured));
        item.setThresholdJson(writeJson(outcome.thresholdSnapshot()));
        // V231 evidence_ids_json 指向 V240 证据表：本批未写证据行 ⇒ 留 NULL（禁造证据 ID，§6.3 只登记事实）
        item.setEvidenceIdsJson(null);
        item.setTenantId(ctx.tenantId());
        item.setOrgId(ctx.orgId());
        item.setVersionNo("1");
        item.setCreateBy(operator);
        item.setUpdateBy(operator);
        return item;
    }

    private WAgentReadinessDO newHead(String assessmentId, ReadinessProbeContext ctx, String profile,
                                      ReadinessGradeResult grade, int checkCount, String operator) {
        WAgentReadinessDO head = new WAgentReadinessDO();
        head.setId(assessmentId);
        head.setQuestionId(ctx.questionId());
        head.setRunId(ctx.runId());
        // V230 profile_id/profile_version 的列语义是"知识画像引用（卷 04）"，非阈值档位：
        // kb 引擎无画像读端点 ⇒ 留 NULL（把阈值档位写进这两列是串语义，档位改落 layer_summary_json.profile）
        head.setProfileId(null);
        head.setProfileVersion(null);
        head.setGrade(grade.grade().value());
        head.setLayerD(layerValue(grade, ReadinessLayer.D));
        head.setLayerI(layerValue(grade, ReadinessLayer.I));
        head.setLayerK(layerValue(grade, ReadinessLayer.K));
        head.setLayerC(layerValue(grade, ReadinessLayer.C));
        head.setLayerSummaryJson(writeJson(layerSummary(profile, grade, ctx.probes(), checkCount)));
        head.setDegradationsJson(writeJson(degradations(grade)));
        head.setComputedTime(LocalDateTime.now());
        // valid_until 留 NULL = 不过期：有效期口径（TTL/是否随数据变更失效）设计未给数值，禁代码自设一个数冒充规格
        head.setValidUntil(null);
        head.setSupersededBy(null);
        head.setIsPinned(0);
        head.setTenantId(ctx.tenantId());
        head.setOrgId(ctx.orgId());
        head.setVersionNo("1");
        head.setCreateBy(operator);
        head.setUpdateBy(operator);
        return head;
    }

    /** DIKC 四层逐层结论 + 探测留痕（按 {@link ReadinessLayer} 声明序重建，禁 {@code Map.copyOf} 的丢序形态）。 */
    Map<String, Object> layerSummary(String profile, ReadinessGradeResult grade,
                                     Map<ReadinessLayer, LayerProbe> probes, int checkCount) {
        Map<String, Object> layers = new LinkedHashMap<>();
        for (ReadinessLayer layer : ReadinessLayer.values()) {
            LayerProbe probe = probes.get(layer);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", layerValue(grade, layer));
            row.put("probe_path", probe == null ? null : probe.path());
            row.put("probe_reachable", probe != null && probe.reachable());
            row.put("probe_status", probe == null ? "not-probed" : probe.status());
            row.put("probe_reason", probe == null ? "layer-not-probed" : probe.reason());
            layers.put(layer.value(), row);
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        for (ReadinessCheckStatus status : ReadinessCheckStatus.values()) {
            counts.put(status.value(), String.valueOf(grade.statusCounts().getOrDefault(status, 0)));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("profile", profile);
        summary.put("matched_rule", grade.matchedRule());
        summary.put("check_count", checkCount);
        summary.put("missing_count", grade.missingItems().size());
        summary.put("status_counts", counts);
        summary.put("layers", layers);
        return summary;
    }

    /**
     * 本次就绪态蕴含的预登记降级码（F10-10 白名单只可关不可开新 ⇒ 码源只取
     * {@link ReadinessCheckCode#degradeCode()}，禁拼新码）。
     *
     * <p>{@code UNKNOWN} 不映射 DG 码：证据缺席≠"确认要走该降级"，把 UNKNOWN 洗成 DG
     * 是给缺证据的层编造一个降级理由。真正发 DG 由 F10-10 在出结论时点决定，本列只是评估侧的
     * "就绪态蕴含哪些预登记降级"留痕。</p>
     */
    List<String> degradations(ReadinessGradeResult grade) {
        Set<String> codes = new LinkedHashSet<>();
        for (ReadinessCheck missing : grade.missingItems()) {
            if (missing.status() == ReadinessCheckStatus.UNKNOWN) {
                continue;
            }
            ReadinessCheckCode.fromCheckCode(missing.checkCode())
                    .map(ReadinessCheckCode::degradeCode)
                    .ifPresent(degrade -> codes.add(degrade.value()));
        }
        return List.copyOf(new ArrayList<>(codes));
    }

    /** DegradeCode 白名单自检（防未来有人把码改成非白名单字面；门禁 degradeCodesAreWhitelisted 消费）。 */
    public static Set<DegradeCode> whitelistableDegradeCodes() {
        Set<DegradeCode> set = new LinkedHashSet<>();
        for (ReadinessCheckCode code : ReadinessCheckCode.values()) {
            if (code.degradeCode() != null) {
                set.add(code.degradeCode());
            }
        }
        return Collections.unmodifiableSet(set);
    }

    private static String layerValue(ReadinessGradeResult grade, ReadinessLayer layer) {
        return grade.layerStatus().getOrDefault(layer, ReadinessCheckStatus.UNKNOWN).value();
    }

    /** 旧评估只写指针（硬规则④），pinned/已被替代行返 0 ⇒ 记 WARN 不抛错。 */
    private void supersedePrevious(String previousAssessmentId, String newAssessmentId, String operator) {
        if (isBlank(previousAssessmentId) || previousAssessmentId.equals(newAssessmentId)) {
            return;
        }
        int moved = readinessDao.markSuperseded(previousAssessmentId, newAssessmentId, operator);
        if (moved == 0) {
            log.warn("[F10-08] 旧评估未被替代（pinned 或已 superseded，F10-07 硬规则④）: previousId={}",
                    previousAssessmentId);
            return;
        }
        gapService.supersedeOpenGaps(previousAssessmentId, operator);
    }

    // ══════════════════════════ 目标反查与序列化 ══════════════════════════

    /** Question→Mission→goal_id（Goal 行本身由取数面按 goalId 现读，本处只给锚点）。 */
    private String resolveGoalId(String missionId) {
        if (isBlank(missionId)) {
            return null;
        }
        Optional<WAgentMissionDO> mission = missionDao.findById(missionId);
        return mission.map(WAgentMissionDO::getGoalId).filter(ReadinessService::nonBlank).orElse(null);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // 快照写不出＝评估不可复盘，宁可拒绝这次评估，也不写一个"看起来成功"的空壳
            throw WAgentOrchestrationException.validation("readiness JSON 快照序列化失败: " + e.getClass().getSimpleName());
        }
    }

    /** 未接线原因串（供数面缺席时的机器可读点名，禁落成 null 让排障无从下手）。 */
    private static String metricNotWiredReason(ReadinessCheckCode code) {
        return REASON_METRIC_NOT_WIRED + code.checkCode();
    }

    private static boolean nonBlank(String v) {
        return v != null && !v.isBlank();
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    /** 档位改进比较的公共口径（A&lt;B&lt;C&lt;D，ordinal 越大越差）——留给响应面复用，禁各处自比字母。 */
    public static boolean isImprovement(ReadinessGrade from, ReadinessGrade to) {
        return from != null && to != null && to.ordinal() < from.ordinal();
    }
}
