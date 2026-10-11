package com.chinacreator.gzcm.workspace.forecast;

import com.chinacreator.gzcm.workspace.scenario.ScenarioDomainException;

/**
 * 关账期间禁重算（H-5，HTTP 409，code=PERIOD_CLOSED）：
 * 命中 {@code ecos.data.facts.closed-periods} 的账期不得重算/重试/复制。
 */
public class ForecastPeriodClosedException extends ScenarioDomainException {

    public ForecastPeriodClosedException(String period, String forecastRunId) {
        super(409, "PERIOD_CLOSED", "账期已关账，禁止重算: period=" + period);
        putDetail("period", period);
        if (forecastRunId != null) {
            putDetail("forecastRunId", forecastRunId);
        }
    }
}
