package com.chinacreator.gzcm.workspace.exception;

/**
 * 场景状态机非法迁移（F07-07 / C-2 / C-5）。HTTP 409。
 * 错误码三者取一：ILLEGAL_TRANSITION（迁移边不存在）/ TERMINAL_STATE（源态终态）/ NO_FORMAL_RUN（激活缺正式运行）。
 */
public class IllegalScenarioTransitionException extends WorkspaceException {

    public static final String CODE_ILLEGAL = "ILLEGAL_TRANSITION";
    public static final String CODE_TERMINAL = "TERMINAL_STATE";
    public static final String CODE_NO_FORMAL_RUN = "NO_FORMAL_RUN";

    public IllegalScenarioTransitionException(String errorCode, String message) {
        super(409, 409, errorCode, message);
    }
}
