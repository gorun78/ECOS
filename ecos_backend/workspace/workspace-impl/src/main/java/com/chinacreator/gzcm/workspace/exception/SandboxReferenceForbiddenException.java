package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.error.FCScenarioErrorCode;

/** 正式运行禁止引用演练产物作基线（F07-09，HTTP 400 / SANDBOX_REF_FORBIDDEN）。 */
public class SandboxReferenceForbiddenException extends WorkspaceDomainException {

    public SandboxReferenceForbiddenException(String baselineRunId) {
        super(400, FCScenarioErrorCode.CODE_SANDBOX_REF_FORBIDDEN,
                "正式运行禁止引用演练产物作为基线: baselineRunId=" + baselineRunId);
        putDetail("baselineRunId", baselineRunId);
    }
}
