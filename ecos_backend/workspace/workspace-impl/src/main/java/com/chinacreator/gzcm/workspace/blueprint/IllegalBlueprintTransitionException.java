package com.chinacreator.gzcm.workspace.blueprint;

import com.chinacreator.gzcm.common.error.ScenarioErrorCode;

/** 非法状态迁移（§6.3 / 八项建议 #3，HTTP 409）。 */
public class IllegalBlueprintTransitionException extends BlueprintDomainException {

    public IllegalBlueprintTransitionException(String code, String message) {
        super(409, code, message);
    }

    public static IllegalBlueprintTransitionException illegal(String from, String to) {
        return new IllegalBlueprintTransitionException(
                ScenarioErrorCode.CODE_ILLEGAL_TRANSITION, "非法状态迁移: " + from + " -> " + to);
    }
}
