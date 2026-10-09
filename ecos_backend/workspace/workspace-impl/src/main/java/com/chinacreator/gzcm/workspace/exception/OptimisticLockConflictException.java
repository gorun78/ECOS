package com.chinacreator.gzcm.workspace.exception;

/** 沙盘 layout 乐观锁冲突（version_no 不匹配，HTTP 409 / LAYOUT_CONFLICT）。 */
public class OptimisticLockConflictException extends WorkspaceDomainException {

    public OptimisticLockConflictException(String scenarioId, String expectedVersion) {
        super(409, "LAYOUT_CONFLICT", "沙盘布局已被他人修改，请刷新后重试");
        putDetail("scenarioId", scenarioId);
        putDetail("expectedVersion", expectedVersion);
    }
}
