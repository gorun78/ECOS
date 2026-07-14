package com.chinacreator.gzcm.runtime.core.agent.exception;

/**
 * Agent 异常
 * Agent 执行过程中的所有异常基类
 *
 * @author CDRC Design Team
 */
public class AgentException extends RuntimeException {

    private String sessionId;
    private String errorCode;

    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }

    public AgentException(String sessionId, String errorCode, String message) {
        super(message);
        this.sessionId = sessionId;
        this.errorCode = errorCode;
    }

    public AgentException(String sessionId, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.sessionId = sessionId;
        this.errorCode = errorCode;
    }

    public String getSessionId() { return sessionId; }
    public String getErrorCode() { return errorCode; }

    // ── 常用错误码 ────────────────────────────

    public static final String ERR_LLM_CALL_FAILED = "AGENT_001";      // LLM 调用失败
    public static final String ERR_TOOL_NOT_FOUND = "AGENT_002";       // 工具未找到
    public static final String ERR_TOOL_EXEC_FAILED = "AGENT_003";     // 工具执行失败
    public static final String ERR_MAX_ITERATIONS = "AGENT_004";       // 超出最大迭代次数
    public static final String ERR_SESSION_NOT_FOUND = "AGENT_005";    // 会话不存在
    public static final String ERR_CONFIG_INVALID = "AGENT_006";       // 配置无效
}
