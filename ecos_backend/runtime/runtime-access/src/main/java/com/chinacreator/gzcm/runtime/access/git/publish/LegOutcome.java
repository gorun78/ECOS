package com.chinacreator.gzcm.runtime.access.git.publish;

/**
 * 单腿执行结果（§2.9.6① 红线「三者分别记录」的最小载体；#147D）。
 *
 * <p>一次发布同时触及三条彼此独立的腿：<b>Git 归档腿</b>（runtime-access 唯一出口）、
 * <b>权威库应用腿</b>（领域 DB 写）、<b>安全审计腿</b>（{@code AuditHashChainService} +
 * Kafka {@code ecos.audit}）。三腿各自的成功/失败<b>不能互相推断</b>，也不能折叠成一枚
 * boolean——折叠即「把部分成功报完全成功」。</p>
 *
 * <p><b>四值不可省</b>：</p>
 * <ul>
 *   <li>{@link #SUCCEEDED} — 该腿有<b>正向回执</b>（如 git 返回真实 commitId、UPDATE 影响行数确认）；</li>
 *   <li>{@link #FAILED} — 该腿<b>确认失败</b>（异常/回执明确否）；</li>
 *   <li>{@link #UNKNOWN} — <b>结果未知</b>（领域 API 超时、回执丢失、补偿队列已受理但未回放）。
 *       既非成功亦非失败：只能凭持久化任务的 {@code taskId} 对账后才能定论（§2.9.6②）。
 *       把 UNKNOWN 当失败会让可重试发布误判报废，把 UNKNOWN 当成功就是伪成功——两者都禁；</li>
 *   <li>{@link #NOT_APPLICABLE} — 该腿本次<b>不适用</b>（通道未装配 / 该类变更本不含此腿）。
 *       必须显式记「不适用 + 原因」，<b>禁与 {@link #SUCCEEDED} 折叠</b>：
 *       现状里本体发布路径从不写审计，若把它记成 SUCCEEDED，
 *       「三腿均成」就凭空成立，§2.9.6① 红线形同虚设。</li>
 * </ul>
 *
 * @author ECOS Runtime底座 #147D
 */
public enum LegOutcome {

    /** 正向回执确认成功。 */
    SUCCEEDED,

    /** 确认失败。 */
    FAILED,

    /** 结果未知（超时/无回执/已入补偿待回放），禁当成功也禁当失败。 */
    UNKNOWN,

    /** 本次不适用（通道未接入或该类变更不含此腿），须附原因，禁折叠成成功。 */
    NOT_APPLICABLE;

    /** 是否确认成功（{@link #NOT_APPLICABLE} 与 {@link #UNKNOWN} 都不算）。 */
    public boolean isSuccess() {
        return this == SUCCEEDED;
    }

    /** 是否确认失败。 */
    public boolean isFailure() {
        return this == FAILED;
    }

    /** 结果是否不可定论（须对账）。 */
    public boolean isIndeterminate() {
        return this == UNKNOWN;
    }

    /**
     * 该腿是否「未产生否定证据」（供 {@code PublishStateMachine.deriveState} 使用）：
     * 成功或不适用。{@link #UNKNOWN} 与 {@link #FAILED} 都会挡住「完全成功」。
     */
    public boolean isCleared() {
        return this == SUCCEEDED || this == NOT_APPLICABLE;
    }
}
