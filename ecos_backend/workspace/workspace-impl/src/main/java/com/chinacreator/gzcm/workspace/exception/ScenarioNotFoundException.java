package com.chinacreator.gzcm.workspace.exception;

/**
 * 场景不存在（含软删 is_deleted）（C-5）。HTTP 404，错误码 SCENARIO_NOT_FOUND。
 */
public class ScenarioNotFoundException extends WorkspaceException {

    public static final String CODE = "SCENARIO_NOT_FOUND";

    public ScenarioNotFoundException(String message) {
        super(404, 404, CODE, message);
    }

    /** 按场景 id 构造（对外单参语义重载）。 */
    public static ScenarioNotFoundException ofId(String scenarioId) {
        return new ScenarioNotFoundException("scenario not found: " + scenarioId);
    }
}
