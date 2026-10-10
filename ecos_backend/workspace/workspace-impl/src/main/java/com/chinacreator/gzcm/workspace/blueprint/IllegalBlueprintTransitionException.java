package com.chinacreator.gzcm.workspace.blueprint;

/** 非法状态迁移（§6.3 / 八项建议 #3，HTTP 409）。 */
public class IllegalBlueprintTransitionException extends BlueprintDomainException {

    public IllegalBlueprintTransitionException(String code, String message) {
        super(409, code, message);
    }

    public static IllegalBlueprintTransitionException illegal(String from, String to) {
        return new IllegalBlueprintTransitionException(
                "ILLEGAL_TRANSITION", "非法状态迁移: " + from + " -> " + to);
    }
}
