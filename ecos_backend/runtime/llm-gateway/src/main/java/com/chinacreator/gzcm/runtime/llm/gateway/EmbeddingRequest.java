package com.chinacreator.gzcm.runtime.llm.gateway;

/**
 * LLM 嵌入请求 — OpenAI 兼容 /embeddings 接口
 *
 * <p>承载输入文本（单条字符串或文本数组）、模型名、可选 provider。
 * 由 {@link LLMGateway#embed(EmbeddingRequest)} 消费，POST 到
 * {@code {baseUrl}/embeddings}。
 *
 * <p>设计说明：EmbeddingRequest 不携带 baseUrl，baseUrl 仍由
 * {@link LLMConfig} / {@code LLMGatewayProperties.Engine.defaultProvider} 的
 * provider → baseUrl 映射解析（与 ChatRequest 一致），保证 baseUrl 解析逻辑单一。
 *
 * <p>与 {@link ChatRequest} 同样的手写 getter/setter 风格（llm-gateway 不依赖 lombok）。
 */
public class EmbeddingRequest {

    /** 输入文本（单条，与 texts 二选一） */
    private String input;

    /** 输入文本数组（批量，与 input 二选一） */
    private String[] texts;

    /** 嵌入模型名（如 text-embedding-3-small）；null 时由 LLMGateway 用默认模型兜底 */
    private String model;

    /** 显式指定 provider（null/blank 时按 model 名自动检测，同 chat detectProvider） */
    private String provider;

    /** 无参构造器 — 供 Jackson 反序列化与 setter 赋值 */
    public EmbeddingRequest() {
    }

    /** 单条文本快捷构造器 */
    public EmbeddingRequest(String input, String model) {
        this.input = input;
        this.model = model;
    }

    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }

    public String[] getTexts() { return texts; }
    public void setTexts(String[] texts) { this.texts = texts; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}
