package com.chinacreator.gzcm.workspace.forecast;

import com.chinacreator.gzcm.common.error.FCScenarioErrorCode;
import com.chinacreator.gzcm.workspace.scenario.ScenarioDomainException;

/** 预测 run 不存在（H-5，HTTP 404，code=FORECAST_RUN_NOT_FOUND）。 */
public class ForecastRunNotFoundException extends ScenarioDomainException {

    public ForecastRunNotFoundException(String forecastRunId) {
        super(404, FCScenarioErrorCode.CODE_FORECAST_RUN_NOT_FOUND, "预测 run 不存在: forecastRunId=" + forecastRunId);
        putDetail("forecastRunId", forecastRunId);
    }
}
