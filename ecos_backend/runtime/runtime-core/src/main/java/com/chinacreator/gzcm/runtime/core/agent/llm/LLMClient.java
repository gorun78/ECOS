package com.chinacreator.gzcm.runtime.core.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM 大模型客户端接口
 * 
 * 统一的大模型调用抽象，屏蔽不同 LLM 提供商的差异。
 * 支持 OpenAI、Azure OpenAI、本地模型（vLLM/Ollama）等。
 *
 * @author CDRC Design Team
 */
public interface LLMClient {

    /**
     * 发送对话请求（非流式）
     *
     * @param request 对话请求（包含消息列表和工具定义）
     * @return LLM 响应（文本回复 + 工具调用指令）
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 发送对话请求（流式回调）
     *
     * @param request 对话请求
     * @param callback 流式回调（每收到一个 token 调用一次）
     * @return 最终的完整响应
     */
    ChatResponse chatStream(ChatRequest request, StreamCallback callback);

    /**
     * 获取可用模型列表
     */
    List<String> getAvailableModels();

    /**
     * 获取当前使用统计
     */
    Map<String, Object> getUsageStats();

    /**
     * 流式回调接口
     */
    @FunctionalInterface
    interface StreamCallback {
        void onToken(String token);
    }
}
