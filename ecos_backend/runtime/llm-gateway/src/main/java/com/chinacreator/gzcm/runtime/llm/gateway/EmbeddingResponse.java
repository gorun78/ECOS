package com.chinacreator.gzcm.runtime.llm.gateway;

import java.util.Collections;
import java.util.List;

/**
 * LLM 嵌入响应 — OpenAI 兼容 /embeddings 接口
 *
 * <p>与 {@link ChatResponse} 同样的静态工厂 / 手写 getter/setter 风格
 * （llm-gateway 不引 Lombok）。
 */
public class EmbeddingResponse {

    /** 上游成功标记 */
    private boolean success;

    /** 错误描述（success=false 时填充） */
    private String errorMsg;

    /** embedding 向量集合，每条输入对应一个 float[] */
    private List<float[]> data = Collections.emptyList();

    /** 实际使用的模型 */
    private String model;

    /** 输入 token 数（usage.prompt_tokens） */
    private long tokensInput;

    /** 总 token 数（usage.total_tokens） */
    private long tokensTotal;

    /** 静态工厂：成功 */
    public static EmbeddingResponse ok(List<float[]> data, String model, long tokensInput, long tokensTotal) {
        EmbeddingResponse r = new EmbeddingResponse();
        r.success = true;
        r.data = data != null ? data : Collections.emptyList();
        r.model = model;
        r.tokensInput = tokensInput;
        r.tokensTotal = tokensTotal;
        return r;
    }

    /** 静态工厂：失败 */
    public static EmbeddingResponse fail(String errorMsg) {
        EmbeddingResponse r = new EmbeddingResponse();
        r.success = false;
        r.errorMsg = errorMsg;
        r.data = Collections.emptyList();
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public List<float[]> getData() { return data; }
    public void setData(List<float[]> data) { this.data = data; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public long getTokensInput() { return tokensInput; }
    public void setTokensInput(long tokensInput) { this.tokensInput = tokensInput; }

    public long getTokensTotal() { return tokensTotal; }
    public void setTokensTotal(long tokensTotal) { this.tokensTotal = tokensTotal; }
}
