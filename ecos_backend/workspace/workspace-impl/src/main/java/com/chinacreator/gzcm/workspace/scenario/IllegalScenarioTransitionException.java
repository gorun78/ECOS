package com.chinacreator.gzcm.workspace.scenario;

/** 非法状态迁移（F07-07 / C-2 迁移表，HTTP 409）。 */
public class IllegalScenarioTransitionException extends ScenarioDomainException {

    public IllegalScenarioTransitionException(String code, String message) {
        super(409, code, message);
    }

    public static IllegalScenarioTransitionException illegal(String from, String to) {
        return new IllegalScenarioTransitionException(
                "ILLEGAL_TRANSITION", "非法状态迁移: " + from + " -> " + to);
    }

    public static IllegalScenarioTransitionException terminal(String from) {
        return new IllegalScenarioTransitionException(
                "TERMINAL_STATE", "终态不可回退: " + from);
    }

    public static IllegalScenarioTransitionException noFormalRun() {
        return new IllegalScenarioTransitionException(
                "NO_FORMAL_RUN", "COMPLETED 需至少一条 FORMAL 运行");
    }

    public static IllegalScenarioTransitionException incomplete() {
        return new IllegalScenarioTransitionException(
                "INCOMPLETE_SCENARIO", "场景完整度判定为空，禁止激活");
    }
}
