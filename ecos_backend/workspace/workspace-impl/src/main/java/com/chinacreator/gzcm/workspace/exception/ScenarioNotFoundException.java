package com.chinacreator.gzcm.workspace.exception;

/** 场景不存在或已软删（全端点，HTTP 404 / SCENARIO_NOT_FOUND）。 */
public class ScenarioNotFoundException extends WorkspaceDomainException {

    public ScenarioNotFoundException(String scenarioId) {
        super(404, "SCENARIO_NOT_FOUND", "场景不存在: " + scenarioId);
        putDetail("scenarioId", scenarioId);
    }
}
