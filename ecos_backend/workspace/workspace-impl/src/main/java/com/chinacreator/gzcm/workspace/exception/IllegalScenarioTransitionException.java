package com.chinacreator.gzcm.workspace.exception;

/** 场景状态机非法迁移（F07-07 / §四 C-2 迁移表，HTTP 409）。 */
public class IllegalScenarioTransitionException extends WorkspaceDomainException {

    /** §四 C-5 要求的三个子判词 —— 前端按钮态据此区分。 */
    public enum Reason {
        ILLEGAL_TRANSITION,
        TERMINAL_STATE,
        NO_FORMAL_RUN
    }

    public IllegalScenarioTransitionException(Reason reason, String message) {
        super(409, reason.name(), message);
        putDetail("reason", reason.name());
    }

    public static IllegalScenarioTransitionException illegal(String from, String to) {
        return new IllegalScenarioTransitionException(Reason.ILLEGAL_TRANSITION,
                "非法状态迁移: " + from + " -> " + to);
    }

    public static IllegalScenarioTransitionException terminal(String from) {
        return new IllegalScenarioTransitionException(Reason.TERMINAL_STATE,
                "终态不可回退: " + from);
    }

    public static IllegalScenarioTransitionException noFormalRun() {
        return new IllegalScenarioTransitionException(Reason.NO_FORMAL_RUN,
                "COMPLETED 需至少一条 FORMAL 运行");
    }
}
