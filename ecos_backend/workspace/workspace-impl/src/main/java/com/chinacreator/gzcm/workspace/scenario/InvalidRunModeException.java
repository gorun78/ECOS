package com.chinacreator.gzcm.workspace.scenario;

/** 运行模式入参非法（卷07 F07-08，HTTP 400，code=INVALID_RUN_MODE）。 */
public class InvalidRunModeException extends ScenarioDomainException {

    public InvalidRunModeException(String value) {
        super(400, "INVALID_RUN_MODE", "非法 run_mode 值: " + value + "（仅允许 FORMAL|SANDBOX）");
        putDetail("runMode", value);
    }
}
