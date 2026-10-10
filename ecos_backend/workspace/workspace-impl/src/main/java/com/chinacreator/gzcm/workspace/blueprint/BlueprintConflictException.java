package com.chinacreator.gzcm.workspace.blueprint;

/** 并发状态迁移/幂等冲突（CAS 从态守卫未命中，HTTP 409 幂等键冲突语义，§13.1）。 */
public class BlueprintConflictException extends BlueprintDomainException {

    public BlueprintConflictException(String blueprintId, String expectedStatus, String attemptedTarget) {
        super(409, "BLUEPRINT_STATE_CONFLICT",
                "蓝图状态已被他人修改（期望 " + expectedStatus + "，迁移目标 " + attemptedTarget + "），请刷新后重试");
        putDetail("blueprintId", blueprintId);
        putDetail("expectedStatus", expectedStatus);
        putDetail("attemptedTarget", attemptedTarget);
    }
}
