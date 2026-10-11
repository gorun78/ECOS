package com.chinacreator.gzcm.workspace.forecast;

import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * 预测 run 状态机判据（H-5）：只持有流转/只读/重试的可执行裁决，落库真态由引擎侧维护。
 *
 * <ul>
 *   <li><b>C210 只读</b>：SUCCEEDED / SUCCEEDED_DEGRADED 落定后禁原地改写结果；</li>
 *   <li><b>重试</b>：仅终态 run 可重试，且重试<b>不改原 run</b>，派生新 run 并以
 *       {@code baselineRunId} 回链源 run（链路可追溯）；</li>
 *   <li><b>幂等</b>：同 key 同 hash ⇒ 复用旧 run；同 key 异 hash ⇒ 冲突拒绝（禁静默覆盖）。</li>
 * </ul>
 */
@Component
public class ForecastRunStateMachine {

    /** 改写落定 run 的结果入口守卫：命中只读态抛 {@link ForecastRunReadOnlyException}。 */
    public void assertRewriteAllowed(ForecastRunView target, String attempt) {
        if (target != null && target.getStatus() != null && target.getStatus().isImmutable()) {
            throw new ForecastRunReadOnlyException(target.getForecastRunId(), attempt);
        }
    }

    /** 重试前置：源 run 必须处于终态（RUNNING 不可重试）。 */
    public void assertRetryable(ForecastRunView base) {
        ForecastRunStatus status = base == null ? null : base.getStatus();
        if (status == null || !status.isTerminal()) {
            throw new ForecastRunConflictException(
                    "retry:" + (base == null ? "?" : base.getForecastRunId()),
                    "terminal",
                    status == null ? "UNKNOWN" : status.name());
        }
    }

    /**
     * 幂等裁决：同 {@code idempotencyKey} 命中既有 run 时，
     * 内容指纹一致 ⇒ 返回需复用的旧 runId；不一致 ⇒ 抛 {@link ForecastRunConflictException}。
     * 未命中既有键 ⇒ 返回 null（走新建）。
     */
    public String resolveIdempotentReuse(ForecastRunView existing, String incomingContentHash) {
        if (existing == null) {
            return null;
        }
        if (!Objects.equals(existing.getContentHash(), incomingContentHash)) {
            throw new ForecastRunConflictException(
                    existing.getIdempotencyKey(), existing.getContentHash(), incomingContentHash);
        }
        return existing.getForecastRunId();
    }
}
