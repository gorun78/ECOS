package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.EmbeddingRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.EmbeddingResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM 网关 REST 控制器 — 暴露 runtime/llm-gateway 的 embed/chat 原语
 *
 * <p>作为「火·W 智慧」引擎（ai-engine）的对外能力，把 {@link LLMGateway}
 * 的向量化与对话原语通过 REST 暴露给其它引擎/服务复用，
 * 是架构铁律 §2.5#2「所有 LLM 调用统一走 llm-gateway」的物料侧。
 *
 * <p>为何落 ai-engine-impl 而非 services/aiming：
 * <ul>
 *   <li>gateway（monolith :8080, 主生产入口）的 {@code @ComponentScan} 扫
 *       {@code com.chinacreator.gzcm.engine}，不扫 {@code com.chinacreator.gzcm.services.aiming}。
 *       kb-engine/cognitive-engine 跑在 gateway 进程内，REST 调用 {@code localhost:8080/api/v1/llm/*}
 *       必须命中 gateway 的扫描包，否则 404。</li>
 *   <li>aiming（:18084，拆分独立部署）也扫 {@code com.chinacreator.gzcm.engine.ai}，同样命中。
 *       两 service/进程都能拿到 bean，平台升级/降级兼容。</li>
 * </ul>
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code POST /api/v1/llm/embedding} — 文本向量化，供 RAG/向量检索等使用</li>
 *   <li>{@code POST /api/v1/llm/chat}      — 对话生成，供 RAG answer 生成/智能问答等使用</li>
 * </ul>
 *
 * <p>三滤波器已放行（pmo-50）：
 * <ul>
 *   <li>{@code VersionPrefixRewriteFilter} — /api/v1/llm 不在 REMOVE map，KEEP</li>
 *   <li>{@code SecurityConfig} — permitAll 加 /api/v1/llm/**</li>
 *   <li>{@code ClearanceInterceptor} — 公开豁免加 /api/v1/llm</li>
 * </ul>
 *
 * <p>请求体使用 {@link EmbeddingRequest} / {@link ChatRequest}（强类型 DTO，
 * 不复用 Map，见后端开发规范「接口不能使用 Map 作为入参和出参」）。
 *
 * @since PMO-50 LLM 网格
 */
@RestController
@RequestMapping("/api/v1/llm")
public class LlmController {

    private static final Logger log = LoggerFactory.getLogger(LlmController.class);

    private final LLMGateway llmGateway;

    public LlmController(LLMGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    /**
     * 文本向量化
     *
     * @param req 强类型请求（input 单条 / texts 多条 二选一 + model 可选）
     * @return OpenAI 兼容嵌入响应；失败时 success=false + errorMsg，code=500
     */
    @PostMapping("/embedding")
    public ApiResponse<EmbeddingResponse> embedding(@Valid @RequestBody EmbeddingRequest req) {
        try {
            if ((req.getInput() == null || req.getInput().isBlank())
                    && (req.getTexts() == null || req.getTexts().length == 0)) {
                return ApiResponse.badRequest("input 或 texts 不能同时为空");
            }
            log.debug("llm/embedding req: model={}, singleInput={}, texts={}",
                    req.getModel(),
                    req.getInput() != null,
                    req.getTexts() != null ? req.getTexts().length : 0);
            EmbeddingResponse resp = llmGateway.embed(req);
            if (resp == null) {
                return ApiResponse.internalError("embed 调用返回空");
            }
            if (!resp.isSuccess()) {
                log.warn("llm/embedding failed: {}", resp.getErrorMsg());
                ApiResponse<EmbeddingResponse> fail = ApiResponse.error(500, "LLM_EMBED_FAIL", resp.getErrorMsg());
                fail.setData(resp);
                return fail;
            }
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("llm/embedding 异常: {}", e.getMessage(), e);
            return ApiResponse.internalError("嵌入调用异常: " + e.getMessage());
        }
    }

    /**
     * LLM 对话生成（非流式）
     *
     * <p>用于 RAG answer 生成等下游场景；agent 编排类需求建议走 agent-service。
     *
     * @param req 强类型请求（messages + model 可选）
     * @return 对话响应；失败时 success=false + errorMsg，code=500
     */
    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest req) {
        try {
            if (req.getMessages() == null || req.getMessages().isEmpty()) {
                return ApiResponse.badRequest("messages 不能为空");
            }
            ChatResponse resp = llmGateway.call(req);
            if (resp == null) {
                return ApiResponse.internalError("chat 调用返回空");
            }
            if (!resp.isSuccess()) {
                log.warn("llm/chat failed: {}", resp.getErrorMsg());
                ApiResponse<ChatResponse> fail = ApiResponse.error(500, "LLM_CHAT_FAIL", resp.getErrorMsg());
                fail.setData(resp);
                return fail;
            }
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("llm/chat 异常: {}", e.getMessage(), e);
            return ApiResponse.internalError("对话调用异常: " + e.getMessage());
        }
    }
}
