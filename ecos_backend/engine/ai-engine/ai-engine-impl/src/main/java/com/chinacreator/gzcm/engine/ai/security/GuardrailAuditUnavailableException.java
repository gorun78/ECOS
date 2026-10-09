package com.chinacreator.gzcm.engine.ai.security;

/**
 * 护栏审计不可用异常（详细设计-06 F06-03 要点 1，铁律 §2.4-5）。
 *
 * <p>护栏审计属红线：不接受"尽力而为"。当 {@code GUARDRAIL_EVAL} 事件无法送达
 * runtime-event 单通道（未装配 / 发布抛异常）时抛出本异常，
 * 由裁决闸转换为 {@code GUARDRAIL_FAIL_CLOSED} 并阻止工具执行
 * （验收：{@code GuardrailAuditEventTest#auditPublishFailureFailsClosed}）。</p>
 */
public class GuardrailAuditUnavailableException extends RuntimeException {

    private final String toolKey;

    public GuardrailAuditUnavailableException(String toolKey, String message, Throwable cause) {
        super(message, cause);
        this.toolKey = toolKey;
    }

    public String toolKey() {
        return toolKey;
    }
}
