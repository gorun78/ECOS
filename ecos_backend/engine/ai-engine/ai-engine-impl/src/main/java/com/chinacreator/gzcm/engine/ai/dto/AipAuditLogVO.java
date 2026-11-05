package com.chinacreator.gzcm.engine.ai.dto;

import java.util.Map;

/**
 * AIP 审计日志行（演示数据）：{@code id/event/agentId/user/action/result/timestamp/details}。
 * {@code details} 为自由附加信息（tokensUsed/durationMs 等），原样透传。
 */
public record AipAuditLogVO(
        String id,
        String event,
        String agentId,
        String user,
        String action,
        String result,
        String timestamp,
        Map<String, Object> details) {
}
