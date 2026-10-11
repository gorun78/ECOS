package com.chinacreator.gzcm.engine.cognitive.api.backtest;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 复审任务路由响应体（详设-09 v1.2 §十一 H-4 要点 4；新端点
 * {@code POST /api/v1/cognitive/outcomes/{evaluationId}/review-tasks} 的 {@code data} 段）。
 *
 * <p>铁律：{@link #taskId} 只准是 runtime-task {@code submitTask} 返回的真实任务 ID
 * （铁律 §1.6 / §2.5 #3，<b>禁自造 jobId、禁自建任务表</b>）；结局非
 * {@link BacktestReviewRouteOutcome#SUBMITTED} 时 {@code taskId} 必为 {@code null}（禁空串占位）。</p>
 *
 * <p>{@link #reviewerRole} 只准来自 security-engine 裁决透传，cognitive 侧<b>零</b>"角色名→权限"
 * 映射表（H-4 要点 4）；{@link #decisionRef} 回显裁决策略引用与入参摘要，保证可复算。</p>
 */
@Data
public class BacktestReviewRouteResult {

    /** 评估记录 ID（V267 主键）。 */
    private String evaluationId;

    /** 本次路由针对的业务运行 ID（复审任务参数必带项）。 */
    private String runId;

    private String scenarioId;

    /** 路由结局（{@link BacktestReviewRouteOutcome} 词表）。 */
    private String outcome;

    /** 是否已建任务（true ⇔ {@code outcome == SUBMITTED}）。 */
    private boolean routed;

    /** runtime-task 返回的真实任务 ID（未建任务 ⇒ null）。 */
    private String taskId;

    /** runtime-task 任务类型（词表单源见 impl 的 {@code COGNITIVE_BACKTEST_REVIEW}）。 */
    private String taskType;

    /** 裁决透出的复审角色（禁本地推断；未给出 ⇒ null 并落 {@link BacktestReviewRouteOutcome#ROLE_DECIDER_RETURNED_NO_ROLE}）。 */
    private String reviewerRole;

    /** 裁决来源定位（{@code policyRef} / 端点 / {@code inputDigest}，可复算锚点）。 */
    private String decisionRef;

    /** 越限项清单（metricRef + 指标名 + 实测值摘要；LOW_SAMPLE 抑制时进 {@link #warnings} 而非此项）。 */
    private List<String> breachedItems = new ArrayList<>();

    /** 复审任务实测值回显（与提交给 runtime-task 的 parameters 同源，禁两处各算一遍）。 */
    private List<String> measuredValues = new ArrayList<>();

    /** 阈值生效来源（逐枚键 → 来源标注，配置单源可核验）。 */
    private List<String> thresholdSources = new ArrayList<>();

    /** 告警/原因（fail-loud，含未建任务的可解释原因）。 */
    private List<String> warnings = new ArrayList<>();

    /** 追踪 ID 回显。 */
    private String traceId;

    public boolean isRouted() {
        return routed;
    }
}
