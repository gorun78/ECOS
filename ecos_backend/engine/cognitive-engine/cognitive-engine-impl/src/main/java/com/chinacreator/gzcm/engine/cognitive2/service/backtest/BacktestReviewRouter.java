package com.chinacreator.gzcm.engine.cognitive2.service.backtest;

import com.chinacreator.gzcm.engine.cognitive.api.backtest.BacktestReviewRouteOutcome;
import com.chinacreator.gzcm.engine.cognitive.api.backtest.BacktestReviewRouteResult;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.model.OutcomeEvaluationEntity;
import com.chinacreator.gzcm.engine.cognitive2.observability.CognitiveObservability;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 复审任务路由器（详设-09 v1.2 §十一 H-4 要点 4 / W226：指标越限 ⇒ 经 runtime-task 建复审任务）。
 *
 * <h3>三条铁律</h3>
 * <ol>
 *   <li><b>任务只走 runtime-task</b>（铁律 §1.6 / §2.5 #3）：唯一出口是
 *       {@link ITaskManagementService#submitTask(TaskDescription)}，{@code taskId} 留空由底座分配
 *       （<b>禁自造 jobId</b>），认知侧不建任务表、不起线程、不用 {@code @Scheduled}；</li>
 *   <li><b>角色只走 security-engine 裁决</b>：{@link BacktestReviewRoleDecider} 未装配或返回不可用
 *       ⇒ 503 fail-closed；裁决 DENY ⇒ 403；两条路径都<b>不建任务</b>；</li>
 *   <li><b>LOW_SAMPLE 优先于越限</b>：守卫触发时越限断言已被抑制，绝不据此建任务
 *       （H-4 要点 2「不给误导性结论」的路由面落地）。</li>
 * </ol>
 *
 * <p>任务参数必带 {@code runId} / 越限项 / 实测值（H-4 验收口径），并与响应回显同源。</p>
 */
@Service
public class BacktestReviewRouter {

    private static final Logger log = LoggerFactory.getLogger(BacktestReviewRouter.class);

    /** runtime-task 任务类型（词表单源 = 本常量；与 {@code COGNITIVE_MENTAL_SCAN} 同族命名）。 */
    public static final String TASK_TYPE = "COGNITIVE_BACKTEST_REVIEW";

    /** 裁决动作名（进 security-engine {@code input.action}；不是权限映射，仅动作标识）。 */
    public static final String REVIEW_ACTION = "ecos:fc:backtest:review:assign";

    private final ITaskManagementService taskManagementService;
    private final ObjectProvider<BacktestReviewRoleDecider> deciderProvider;
    private final CognitiveObservability observability;

    public BacktestReviewRouter(ITaskManagementService taskManagementService,
                               ObjectProvider<BacktestReviewRoleDecider> deciderProvider,
                               CognitiveObservability observability) {
        this.taskManagementService = taskManagementService;
        this.deciderProvider = deciderProvider;
        this.observability = observability;
    }

    /** 裁决接缝是否已接线（供 preflight/健康面；false ≠ 假绿）。 */
    public boolean deciderReady() {
        return deciderProvider.getIfAvailable() != null;
    }

    /**
     * 路由一次复审任务。
     *
     * @param entity   既有评估记录（runId/scenarioId/tenantId/orgId 一律取库内值，禁前端传值）
     * @param summary  回测指标面（越限项与守卫态来自 {@link BacktestMetricCalculator}）
     * @param traceId  追踪 ID
     * @throws CognitiveGuardException 403（裁决 DENY / 无角色）或 503（裁决未接线、不可用、建任务失败）
     */
    public BacktestReviewRouteResult route(OutcomeEvaluationEntity entity,
                                           BacktestMetricCalculator.Result computed,
                                           String traceId) {
        BacktestReviewRouteResult r = new BacktestReviewRouteResult();
        r.setEvaluationId(entity.getId());
        r.setRunId(entity.getRunId());
        r.setScenarioId(entity.getScenarioId());
        r.setTaskType(TASK_TYPE);
        r.setTraceId(traceId);
        List<String> warnings = r.getWarnings();
        BacktestMetricSummaryish s = BacktestMetricSummaryish.of(computed);
        r.setThresholdSources(thresholdSourceLines(s));

        if (s.lowSample()) {
            r.setOutcome(BacktestReviewRouteOutcome.SUPPRESSED_BY_LOW_SAMPLE.name());
            r.setRouted(false);
            warnings.add("LOW_SAMPLE: " + s.lowSampleReason());
            warnings.add("NO_REVIEW_TASK_CREATED: breached assertions were suppressed by the guard,"
                    + " routing on them would fabricate a finding");
            observability.audit("cognitiveBacktest.reviewRoute", "outcome_evaluation",
                    "suppressedByLowSample evaluationId=" + entity.getId() + " measured=" + s.measured());
            return r;
        }
        if (s.breaches().isEmpty()) {
            r.setOutcome(BacktestReviewRouteOutcome.NO_BREACH.name());
            r.setRouted(false);
            warnings.add("NO_BREACH: no assertion exceeded its configured threshold, nothing to review");
            return r;
        }
        r.setBreachedItems(new ArrayList<>(s.breaches()));
        r.setMeasuredValues(new ArrayList<>(s.measuredValues()));

        BacktestReviewRoleDecider decider = deciderProvider.getIfAvailable();
        if (decider == null) {
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN,
                    BacktestReviewRouteOutcome.ROLE_DECIDER_UNAVAILABLE.name()
                    + ": BacktestReviewRoleDecider bean not wired (security-engine REST adjudicator absent);"
                    + " fail-closed, no review task created");
        }
        BacktestReviewRoleDecider.ReviewRoleDecision decision = decider.decide(
                new BacktestReviewRoleDecider.ReviewRoleQuery(REVIEW_ACTION, entity.getId(), entity.getRunId(),
                        entity.getScenarioId(), entity.getTenantId(), entity.getOrgId(),
                        s.breaches(), s.measuredValues(), s.thresholdSources()));
        r.setDecisionRef(decision == null ? null : decision.decisionRef());
        if (decision == null || decision.unavailable()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN,
                    BacktestReviewRouteOutcome.ROLE_DECIDER_UNAVAILABLE.name() + ": "
                    + (decision == null ? "decider returned null decision (treated as unavailable)"
                            : decision.reason()));
        }
        if (!decision.allowed()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E403_CLEARANCE,
                    CognitiveErrorCode.MSG_CLEARANCE_DENIED,
                    BacktestReviewRouteOutcome.ROLE_DENIED_BY_POLICY.name() + ": " + decision.reason()
                    + " decisionRef=" + decision.decisionRef());
        }
        if (decision.reviewerRole() == null || decision.reviewerRole().isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E403_CLEARANCE,
                    CognitiveErrorCode.MSG_CLEARANCE_DENIED,
                    BacktestReviewRouteOutcome.ROLE_DECIDER_RETURNED_NO_ROLE.name() + ": " + decision.reason()
                    + " (cognitive never infers a role locally)");
        }
        r.setReviewerRole(decision.reviewerRole());

        String taskId = submit(entity, s, decision, traceId);
        r.setTaskId(taskId);
        r.setOutcome(BacktestReviewRouteOutcome.SUBMITTED.name());
        r.setRouted(true);
        observability.audit("cognitiveBacktest.reviewRoute", "outcome_evaluation",
                "submitted evaluationId=" + entity.getId() + " taskId=" + taskId
                        + " role=" + decision.reviewerRole() + " breaches=" + s.breaches().size());
        log.info("H-4 复审任务已提交 runtime-task: evaluationId={} taskId={} role={} breaches={}",
                entity.getId(), taskId, decision.reviewerRole(), s.breaches().size());
        return r;
    }

    /** 阈值来源清单（形如 {@code key=value} / {@code key=(UNSET)}，逐枚可核验，供响应与任务参数共用）。 */
    private static List<String> thresholdSourceLines(BacktestMetricSummaryish s) {
        List<String> out = new ArrayList<>();
        s.thresholdSources().forEach((k, v) -> out.add(k + "=" + v));
        return out;
    }

    /** 提交 runtime-task（taskId 由底座分配；失败如实 503，禁谎称已建）。 */
    private String submit(OutcomeEvaluationEntity entity, BacktestMetricSummaryish s,
                          BacktestReviewRoleDecider.ReviewRoleDecision decision, String traceId) {
        TaskDescription description = new TaskDescription();
        description.setTaskName("cognitive-backtest-review");
        description.setTaskType(TASK_TYPE);
        description.setDescription("详设-09 H-4 回测越限复审：evaluationId=" + entity.getId());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("evaluationId", entity.getId());
        params.put("runId", entity.getRunId());
        params.put("forecastRunId", entity.getForecastRunId());
        params.put("scenarioId", entity.getScenarioId());
        params.put("breachedItems", s.breaches());
        params.put("measuredValues", s.measuredValues());
        params.put("reviewerRole", decision.reviewerRole());
        params.put("decisionRef", decision.decisionRef());
        params.put("thresholdSources", s.thresholdSources());
        params.put("traceId", traceId);
        description.setParameters(params);
        description.setTenantId(entity.getTenantId());
        description.setCreatedBy("cognitive-backtest-router");
        try {
            return taskManagementService.submitTask(description);
        } catch (ITaskManagementService.TaskManagementException | RuntimeException e) {
            throw new CognitiveGuardException(CognitiveErrorCode.E502_DEPENDENCY_DOWN,
                    CognitiveErrorCode.MSG_DEPENDENCY_DOWN,
                    BacktestReviewRouteOutcome.TASK_SUBMISSION_FAILED.name()
                    + ": runtime-task submitTask failed: " + e.getClass().getSimpleName());
        }
    }

    /**
     * 计算器产物 → 路由输入的收敛视图（避免在本类重复一份判定）。
     *
     * @param measuredValues 逐越限项的实测值摘要（与 breachedItems 同源同序）
     */
    public record BacktestMetricSummaryish(boolean lowSample,
                                           String lowSampleReason,
                                           int measured,
                                           List<String> breaches,
                                           List<String> measuredValues,
                                           Map<String, String> thresholdSources) {

        static BacktestMetricSummaryish of(BacktestMetricCalculator.Result computed) {
            boolean low = computed.summary().isLowSample();
            List<String> breached = low ? List.of() : new ArrayList<>(computed.breaches());
            // 每条越限项自身携带实测值（形如 "GATE measured=0.4200 threshold=0.5000"），
            // 任务参数与响应回显同用一份字符串，禁两处各拼一遍造成漂移。
            return new BacktestMetricSummaryish(low, computed.summary().getLowSampleReason(),
                    computed.summary().getMeasuredSampleCount(), breached, breached,
                    computed.summary().getThresholdSources());
        }

        public Map<String, String> thresholdSources() {
            return thresholdSources == null ? Map.of() : thresholdSources;
        }
    }
}
