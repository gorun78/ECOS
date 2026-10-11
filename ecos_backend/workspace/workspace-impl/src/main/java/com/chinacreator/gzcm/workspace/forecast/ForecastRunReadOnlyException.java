package com.chinacreator.gzcm.workspace.forecast;

import com.chinacreator.gzcm.workspace.scenario.ScenarioDomainException;

/**
 * 只读 run 禁改写（H-5 / C210，HTTP 409，code=RUN_READ_ONLY）：
 * SUCCEEDED 落定后禁重写结果，重试须派生新 run 并回链 baselineRunId。
 */
public class ForecastRunReadOnlyException extends ScenarioDomainException {

    public ForecastRunReadOnlyException(String forecastRunId, String attempt) {
        super(409, "RUN_READ_ONLY", "已成功的预测 run 为只读，禁止改写: forecastRunId=" + forecastRunId);
        putDetail("forecastRunId", forecastRunId);
        putDetail("attempt", attempt);
    }
}
