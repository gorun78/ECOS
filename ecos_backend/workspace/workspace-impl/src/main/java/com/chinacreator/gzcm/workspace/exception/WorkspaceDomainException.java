package com.chinacreator.gzcm.workspace.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * workspace 域异常基类（卷07 F07-13 / §四 C-5）—— 携带真实 HTTP 状态、业务 code 与结构化 details。
 *
 * <p>HTTP 映射由 {@link WorkspaceExceptionHandler} 唯一持有；抛出点禁止再用
 * {@code ApiResponse.error(...)} 伪装 HTTP 200（X-16 / X-17）。</p>
 */
public abstract class WorkspaceDomainException extends RuntimeException {

    private final int httpStatus;
    private final String code;
    private final Map<String, Object> details = new LinkedHashMap<>();

    protected WorkspaceDomainException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    protected WorkspaceDomainException(int httpStatus, String code, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return Collections.unmodifiableMap(details);
    }

    protected void putDetail(String key, Object value) {
        details.put(key, value);
    }
}
