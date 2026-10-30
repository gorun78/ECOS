package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.error.ScenarioErrorCode;

/** 场景不存在或已软删（全端点，HTTP 404 / SCENARIO_NOT_FOUND）。 */
public class ScenarioNotFoundException extends WorkspaceDomainException {

    public ScenarioNotFoundException(String scenarioId) {
        super(404, ScenarioErrorCode.CODE_SCENARIO_NOT_FOUND, "场景不存在: " + scenarioId);
        putDetail("scenarioId", scenarioId);
    }
}
