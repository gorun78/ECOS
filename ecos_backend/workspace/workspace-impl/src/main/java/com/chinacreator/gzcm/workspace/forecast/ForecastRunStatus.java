package com.chinacreator.gzcm.workspace.forecast;

/**
 * 预测 run 状态机词汇（H-5，复用/延展既有场景运行状态串，禁另立一套）。
 *
 * <p>既有 {@code ScenarioRunService} 落库串为 {@code RUNNING}/{@code SUCCEEDED}/
 * {@code SUCCEEDED_DEGRADED}，本枚举把同一词汇结构化，补 {@code FAILED}；
 * 持久化仍以 {@link #name()} 字符串落 {@code status} 列（列语义不变）。</p>
 */
public enum ForecastRunStatus {
    RUNNING,
    SUCCEEDED,
    SUCCEEDED_DEGRADED,
    FAILED;

    /** 终态：不再流转，可作为重试/复制的源。 */
    public boolean isTerminal() {
        return this != RUNNING;
    }

    /**
     * C210 只读：SUCCEEDED / SUCCEEDED_DEGRADED 结果一旦落定即不可原地重写，
     * 任何修订只能经 retry 派生新 run（{@code reusedFromRunId} 回链）。
     */
    public boolean isImmutable() {
        return this == SUCCEEDED || this == SUCCEEDED_DEGRADED;
    }

    public static ForecastRunStatus fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ForecastRunStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        return null;
    }
}
