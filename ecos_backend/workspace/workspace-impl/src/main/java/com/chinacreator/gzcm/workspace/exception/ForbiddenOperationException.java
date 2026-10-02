package com.chinacreator.gzcm.workspace.exception;

/**
 * 场景域权限拒绝（F07-18 / C-5）。security-engine evaluate DENY 或不可用默认 DENY。
 * HTTP 403，错误码 PERMISSION_DENIED；文案不泄露资源存在性。
 */
public class ForbiddenOperationException extends WorkspaceException {

    public static final String CODE = "PERMISSION_DENIED";

    public ForbiddenOperationException(String message) {
        super(403, 403, CODE, message);
    }
}
