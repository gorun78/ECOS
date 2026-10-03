package com.chinacreator.gzcm.runtime.llm.usage;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.common.context.TraceContext;
import com.chinacreator.gzcm.runtime.llm.repository.LlmUsageMapper;

/**
 * LLM token 计量录制器（F00-07 / E 章，V244）。
 * <p>
 * 轻量接入点：{@link #record} 单方法落 {@code public.ecos_runtime_llm_usage}，
 * 全链 try/catch — 计量失败绝不阻断 LLM 主路径（可用性红线）。
 *
 * <pre>
 * TODO 挂接位（不强行改 LLMGatewayImpl 主路径，任务边界内暂缓）：
 *   唯一 LLM 出口 {@code runtime.llm.gateway.LLMGatewayImpl} 已内建 token 计量：
 *   - {@code doEmbed}      — 响应解析 usage.prompt_tokens 处（tokensInput 已有）
 *   - {@code streamChat}   — 流式收尾 tokensInput/tokensOutput 累积（ATOMIC 变量已存在）
 *   在这两处（或 {@code AgentMetricsImpl} 的 token 汇总节点）追加：
 *     llmUsageRecorder.record(provider, model, purpose, promptTokens, completionTokens, cost);
 *   即完成 F00-07 E 章的完整量产接入。
 * </pre>
 *
 * @author ECOS-PMO F00-07
 */
@Component
public class LlmUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageRecorder.class);
    private static final String VERSION_NO = "1";
    private static final String DOMAIN = "default";
    /** purpose 白名单（V244 注释口径） */
    public static final String PURPOSE_CHAT = "chat";
    public static final String PURPOSE_EMBEDDING = "embedding";
    public static final String PURPOSE_RERANK = "rerank";

    private final LlmUsageMapper llmUsageMapper;

    public LlmUsageRecorder(LlmUsageMapper llmUsageMapper) {
        this.llmUsageMapper = llmUsageMapper;
    }

    /**
     * 记录一次 LLM 调用的 token 计量。
     *
     * @param provider       provider 标识（如 deepseek / openai，V244 VARCHAR(32)）
     * @param model          模型名（如 deepseek-chat，V244 VARCHAR(64)）
     * @param purpose        chat|embedding|rerank
     * @param promptTokens   输入 token 数（&lt;0 时归 0）
     * @param completionTokens 输出 token 数（&lt;0 时归 0）
     * @param costAmount     成本金额（NUMERIC(18,6)；未知传 null）
     * @param callerModule   调用模块（如 ai-engine / kb-engine）
     * @param callerId       调用方 id（agentId / pipelineId 等）
     * @param scenarioId     场景 id（可 null）
     * @return 落库行 id（落库失败时返回 null，调用方据 null 自行 debug log，<b>禁止因此重试/抛异常</b>）
     */
    public String record(String provider, String model, String purpose,
                         int promptTokens, int completionTokens, BigDecimal costAmount,
                         String callerModule, String callerId, String scenarioId) {
        try {
            LlmUsageEntity row = new LlmUsageEntity();
            row.setId(UUID.randomUUID().toString());
            row.setProvider(provider != null && provider.length() <= 32 ? provider : "unknown");
            row.setModel(model != null && model.length() <= 64 ? model : "unknown");
            row.setPurpose(purpose != null && !purpose.isBlank() ? purpose : PURPOSE_CHAT);
            row.setPromptTokens(Math.max(promptTokens, 0));
            row.setCompletionTokens(Math.max(completionTokens, 0));
            row.setCostAmount(costAmount);
            row.setCallerModule(callerModule);
            row.setCallerId(callerId);
            row.setScenarioId(scenarioId);
            row.setTraceId(TraceContext.current());
            row.setVersionNo(VERSION_NO);
            row.setIsDeleted((short) 0);
            row.setDomain(DOMAIN);
            llmUsageMapper.insert(row);
            return row.getId();
        } catch (Exception e) {
            // 计量绝不阻断 LLM 主流程（可用性红线）
            log.warn("[LlmUsage] 计量落库失败 provider={} model={} errType={} msg={}",
                    provider, model, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
}
