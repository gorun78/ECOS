package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.exception.DataBridgeException;

/**
 * workspace 场景域领域异常基类（详细设计-07 F07-13 / C-5）。
 * <p>在 {@link DataBridgeException} 的 httpStatus/errorCode 之上追加一个面向前端的
 * 字符串错误码（C-5 列），供前端按码分支渲染错误态。响应体一律由
 * {@link WorkspaceExceptionHandler} 统一产出，禁止 Controller 各自 return 伪装码。</p>
 */
public abstract class WorkspaceException extends DataBridgeException {

    private final String errorCode;

    protected WorkspaceException(int httpStatus, int code, String errorCode, String message) {
        super(httpStatus, code, message);
        this.errorCode = errorCode;
    }

    protected WorkspaceException(int httpStatus, int code, String errorCode, String message, Throwable cause) {
        super(httpStatus, code, message, cause);
        this.errorCode = errorCode;
    }

    /** 面向前端的稳定错误码（C-5 表），区别于 {@link #getErrorCode()} 的整型码。 */
    public String getErrorCodeString() {
        return errorCode;
    }
}
