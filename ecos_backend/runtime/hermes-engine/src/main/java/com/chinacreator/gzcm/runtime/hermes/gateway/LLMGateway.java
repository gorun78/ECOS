package com.chinacreator.gzcm.runtime.hermes.gateway;

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
     * 检测 LLM 配置是否可用（连通性测试）
     */
    boolean isAvailable(LLMConfig config);

    /**
     * 获取所有 provider 的运行状态
     *
     * @return Map&lt;providerName, statusJson&gt;
     */
    Map<String, Object> getProviderStatus();
}
