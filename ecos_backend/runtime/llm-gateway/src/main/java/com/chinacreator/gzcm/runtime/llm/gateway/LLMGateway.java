package com.chinacreator.gzcm.runtime.llm.gateway;

import java.util.Map;

/**
 * LLM 调用网关接口 — 屏蔽不同 provider 的 API 差异
 */
public interface LLMGateway {

    /**
     * 调用 LLM 并返回完整响应
     */
    ChatResponse call(ChatRequest request);

    /**
     * 带重试的 LLM 调用
     *
     * @param request    请求
     * @param maxRetries 最大重试次数（不含首次）
     * @return 最后一次请求的响应
     */
    ChatResponse callWithRetry(ChatRequest request, int maxRetries);

    /**
     * 调用 LLM 并返回嵌入向量（POST {baseUrl}/embeddings）
     *
     * <p>公共底座能力，全引擎复用（铁律§2.5#1）。
     *
     * @param request {@link EmbeddingRequest}，{@code input} 与 {@code texts} 二选一；
     *                {@code model}/{@code provider} 一个可空，按 {@code LLMGatewayProperties.Engine} 兜底
     * @return {@link EmbeddingResponse}；失败时 {@code success=false} 与 {@code errorMsg}
     */
    EmbeddingResponse embed(EmbeddingRequest request);

    /**
     * 检测 LLM 配置是否可用（连通性测试）
     */
    boolean isAvailable(LLMConfig config);

    /**
     * 获取所有 provider 的运行状态
     *
     * @return Map&lt;providerName, statusJson&gt;
     */
    Map<String, Object> getProviderStatus();

    /**
     * 会话累计 token 用量（详细设计-06 F06-07 要点 3 的配额判定权威读数）。
     *
     * <p>唯一计量点在本网关（{@code LlmUsageMeter}），读数即本网关落 {@code sys_agent_call_log}
     * 的同会话 input+output 累计；引擎侧不得自行记账。只增方法（API 只增不改）。</p>
     *
     * @param sessionId 会话标识；null/空 → 0（无法界定作用域时不申报用量）
     * @return 累计 token（无记录为 0）
     */
    long sessionTokenUsage(String sessionId);
}
