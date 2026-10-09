package com.chinacreator.gzcm.workspace.scenario;

/** 正式运行禁止引用演练基线（F07-09，HTTP 400，code=SANDBOX_REF_FORBIDDEN）。 */
public class SandboxReferenceForbiddenException extends ScenarioDomainException {

    public SandboxReferenceForbiddenException(String baselineRunId) {
        super(400, "SANDBOX_REF_FORBIDDEN",
                "正式运行禁止引用演练产物作为基线: baselineRunId=" + baselineRunId);
        putDetail("baselineRunId", baselineRunId);
    }
}
