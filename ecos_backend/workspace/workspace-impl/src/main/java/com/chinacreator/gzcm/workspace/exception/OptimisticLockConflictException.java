package com.chinacreator.gzcm.workspace.exception;

/**
 * 沙盘 layout 乐观锁版本冲突（F07 / C-5）。HTTP 409，错误码 LAYOUT_CONFLICT。
 */
public class OptimisticLockConflictException extends WorkspaceException {

    public static final String CODE = "LAYOUT_CONFLICT";

    public OptimisticLockConflictException(String message) {
        super(409, 409, CODE, message);
    }
}
