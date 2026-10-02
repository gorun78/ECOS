package com.chinacreator.gzcm.workspace.exception;

/**
 * workspace 域内部错误（C-5 兜底）。HTTP 500，错误码 INTERNAL；
 * <p>响应体只给通用文案 + traceId，异常原文只进日志，绝不入体（X-41 / 铁律 §0.2.1）。</p>
 */
public class WorkspaceInternalException extends WorkspaceException {

    public static final String CODE = "INTERNAL";

    public WorkspaceInternalException(String message) {
        super(500, -1, CODE, message);
    }

    public WorkspaceInternalException(String message, Throwable cause) {
        super(500, -1, CODE, message, cause);
    }
}
