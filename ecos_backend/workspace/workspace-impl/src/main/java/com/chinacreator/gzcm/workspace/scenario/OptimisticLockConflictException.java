package com.chinacreator.gzcm.workspace.scenario;

/** 沙盘布局乐观锁冲突（卷07 F07-13 C-5，HTTP 409，code=LAYOUT_CONFLICT）。 */
public class OptimisticLockConflictException extends ScenarioDomainException {

    public OptimisticLockConflictException(String scenarioId, int expectedVersion) {
        super(409, "LAYOUT_CONFLICT", "布局已被他人修改，请刷新: scenarioId=" + scenarioId + ", expectedVersion=" + expectedVersion);
        putDetail("scenarioId", scenarioId);
        putDetail("expectedVersion", expectedVersion);
    }
}
