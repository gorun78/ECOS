package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.token.TokenEstimates;

/**
 * Token 估算器 — 引擎侧消息结构 → 文本展开后交公共底座单源估算。
 * <p>
 * 启发式系数（中文 1.5/字、英文 1.3/词、其余 1.0/字符、role 固定 4）已收口到
 * {@link TokenEstimates}（F06-07 要点 1：llm-gateway 出口计量与引擎上下文预算必须同口径，
 * 禁两处各写一份系数导致"估算漂移"）。本类只负责把 {@link Message} 的 role 开销、
 * content 与 tool_calls 展开为可估算的文本。
 * </p>
 */
public class TokenEstimator {

    /**
     * 估算纯文本的 token 数（转引公共底座单源）。
     *
     * @param text 待估算文本（可为 null）
     * @return 估算 token 数（null / 空字符串返回 0）
     */
    public static int estimate(String text) {
        return TokenEstimates.estimateText(text);
    }

    /**
     * 估算一条 Message 的总 token 数。
     * <p>
     * 计算方式：role 开销 + content 文本估算。assistant 消息携带 tool_calls 时，
     * 额外按 name + arguments 文本估算。
     * </p>
     *
     * @param msg 消息对象（可为 null）
     * @return 估算 token 数
     */
    public static int estimate(Message msg) {
        if (msg == null) {
            return 0;
        }

        double tokens = TokenEstimates.ROLE_OVERHEAD_TOKENS;

        if (msg.getContent() != null) {
            tokens += TokenEstimates.estimateText(msg.getContent());
        }

        if (msg.hasToolCalls() && msg.getToolCalls() != null) {
            for (ToolCall tc : msg.getToolCalls()) {
                StringBuilder sb = new StringBuilder();
                sb.append(tc.getName());
                if (tc.getArguments() != null) {
                    sb.append(tc.getArguments());
                }
                tokens += TokenEstimates.estimateText(sb.toString());
            }
        }

        return (int) Math.ceil(tokens);
    }
}
