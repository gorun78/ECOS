package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.error.ScenarioErrorCode;

/** 场景不存在 / 已软删（卷07 F07-13，HTTP 404，code=SCENARIO_NOT_FOUND）。 */
public class ScenarioNotFoundException extends ScenarioDomainException {

    public ScenarioNotFoundException(String scenarioId) {
        super(404, ScenarioErrorCode.CODE_SCENARIO_NOT_FOUND, "场景不存在或已删除: " + scenarioId);
        putDetail("scenarioId", scenarioId);
    }
}
