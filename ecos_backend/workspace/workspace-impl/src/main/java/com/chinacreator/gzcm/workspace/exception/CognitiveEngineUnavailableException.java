package com.chinacreator.gzcm.workspace.exception;

/**
 * 认知引擎不可用（F07-12 四端点 / F07-08 演练 / C-5）。HTTP 503，错误码 COGNITIVE_UNAVAILABLE。
 * <p>A3 红线：引擎不可用一律 503 原样穿透，禁 HTTP 200 + 空 body。</p>
 */
public class CognitiveEngineUnavailableException extends WorkspaceException {

    public static final String CODE = "COGNITIVE_UNAVAILABLE";

    public CognitiveEngineUnavailableException(String message, Throwable cause) {
        super(503, -503, CODE, "cognitive engine unavailable: " + message, cause);
    }

    public CognitiveEngineUnavailableException(String message) {
        super(503, -503, CODE, "cognitive engine unavailable: " + message);
    }
}
