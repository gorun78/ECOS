package com.chinacreator.gzcm.workspace.blueprint;

/** 场景蓝图版本行不存在（HTTP 404）。 */
public class BlueprintVersionNotFoundException extends BlueprintDomainException {

    public BlueprintVersionNotFoundException(String scenarioId, int version) {
        super(404, "BLUEPRINT_VERSION_NOT_FOUND",
                "场景蓝图版本不存在: scenario=" + scenarioId + ", version=" + version);
        putDetail("scenarioId", scenarioId);
        putDetail("version", version);
    }
}
