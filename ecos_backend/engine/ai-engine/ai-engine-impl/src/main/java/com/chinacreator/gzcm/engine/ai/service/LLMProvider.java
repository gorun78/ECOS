package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;

/**
 * LLM Provider 抽象接口 — 屏蔽不同 LLM 厂商的 API 差异。
 * <p>
 * AgentLoopService 遍历已注入的 Provider 列表，
 * 按 {@link #priority()} 排序后选择第一个支持 function-calling 的提供商进行调用。
 * 如无可用 Provider，回退到 {@code LLMGatewayService} 的原生调用路径。
 * </p>
 *
 * <h3>实现约定</h3>
 * <ul>
 *   <li>实现类必须标注 {@code @Component}，确保 Spring 自动发现</li>
 *   <li>{@link #priority()} 越小优先级越高（0=最高优先）</li>
 *   <li>{@link #chat(ChatRequest)} 是阻塞调用，内部自行处理超时/重试</li>
 *   <li>若 api-key 为空或不可用，应通过 {@code @ConditionalOnProperty} 或构造内检查静默跳过</li>
 * </ul>
 */
public interface LLMProvider {

    /**
     * 调用 LLM 并返回完整响应（阻塞）。
     *
     * @param request 统一请求模型（model / messages / temperature / maxTokens / apiKey）
     * @return 统一响应模型（content / tokens / success / errorMsg）
     */
    ChatResponse chat(ChatRequest request);

    /**
     * Provider 名称（如 "deepseek", "openai", "anthropic"），用于日志和状态展示。
     */
    String getName();

    /**
     * 是否支持原生 function-calling（tool_choice / tool_calls）。
     * <p>
     * AgentLoopService 在选择 Provider 时优先挑选返回 {@code true} 的实现。
     * </p>
     */
    boolean supportsFunctionCalling();

    /**
     * 优先级 — 数值越小优先级越高。
     * <p>
     * Spring 注入 {@code List<LLMProvider>} 时，
     * AgentLoopService 会按此字段升序排列，选择第一个可用者。
     * 默认值为 100（较低优先级）。
     * </p>
     */
    default int priority() {
        return 100;
    }

    /**
     * 是否已配置且可用（api-key 非空、base-url 可达等）。
     * <p>
     * 默认实现仅检查 name 非空；子类应覆盖为真正的连通性判断。
     * </p>
     */
    default boolean isAvailable() {
        return getName() != null && !getName().isBlank();
    }
}
