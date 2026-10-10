package com.chinacreator.gzcm.engine.ai.capability;

import java.util.Set;

/**
 * 能力调用台账状态词表（《补充设计 v1.0》§10.4 执行状态 + V269 {@code status} 列七态同词表）。
 *
 * <p><b>降级真值纪律（§10.6）</b>：降级导致质量门槛不满足必须 {@link #DEGRADED}
 * 或 {@link #FAILED}，<b>禁</b>伪装 {@link #SUCCEEDED}（SUP-MODEL-02 可观测：
 * 错误率/降级率按本词表统计）。OUTCOME 三态（SUCCEEDED/DEGRADED/FAILED）
 * 即统一结果结构（§10.5）的 status 语义面，单一词表不另造第二套。</p>
 */
public enum CapabilityState {
    /** 已受理（默认初态，V269 status 默认值）。 */
    PENDING,
    /** 已提交 runtime-task（异步通道受理回执）。 */
    SUBMITTED,
    /** 执行中（同步通道直接进本态）。 */
    RUNNING,
    /** 成功且满足质量门槛。 */
    SUCCEEDED,
    /** 降级可用（备用模型/截断）；质量不达标的必须走本态或 FAILED。 */
    DEGRADED,
    /** 失败。 */
    FAILED,
    /** 已取消。 */
    CANCELED;

    /** 终态集合（台账不再迁移）。 */
    public static Set<CapabilityState> terminal() {
        return Set.of(SUCCEEDED, DEGRADED, FAILED, CANCELED);
    }

    public boolean isTerminal() {
        return terminal().contains(this);
    }

    /** 解析词表值（大小写不敏感；非法值抛 IAE，禁宽容映射）。 */
    public static CapabilityState parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("能力状态缺失（V269 status NOT NULL）");
        }
        try {
            return CapabilityState.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("非法能力状态(仅七态): " + value, e);
        }
    }
}
