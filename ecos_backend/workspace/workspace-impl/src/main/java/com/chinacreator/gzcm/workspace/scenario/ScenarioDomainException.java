package com.chinacreator.gzcm.workspace.scenario;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 场景域领域异常基类 — 卷07 F07-13 的抛出侧载体。
 *
 * <p>本类只负责携带真实 HTTP 状态码 + 业务 code + 结构化 details；HTTP 映射由
 * {@code WorkspaceExceptionHandler}（卷07 另一改造项，属主 Agent）唯一持有。
 * 抛出点禁再用 {@code ApiResponse.error(...)} 伪装 200。</p>
 */
public abstract class ScenarioDomainException extends RuntimeException {

    private final int httpStatus;
    private final String code;
    private final Map<String, Object> details;

    protected ScenarioDomainException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.details = new LinkedHashMap<>();
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
