package com.chinacreator.gzcm.engine.ai.oag;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.oag.dto.OagChatRequest;
import com.chinacreator.gzcm.engine.ai.oag.dto.OagChatResponse;
import com.chinacreator.gzcm.engine.ai.oag.dto.OagStepEvent;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * OAG Pipeline Controller — SSE 流式端点 + 非流式端点（双模式）。
 *
 * <pre>
 *   POST /api/v1/oag/chat          — 非流式对话 (Map 入参, 向后兼容)
 *   POST /api/v1/oag/chat/v2       — 非流式对话 (强类型 DTO 入参)
 *   POST /api/v1/oag/chat/stream   — SSE 流式对话 (text/event-stream)
 *   GET  /api/v1/oag/chat/health   — 管道健康检查
 * </pre>
 *
 * <p>SSE 事件类型（事件名保持不变，payload 改为强类型序列化）：</p>
 * <ul>
 *   <li>{@code node}      — 每个节点完成时推送</li>
 *   <li>{@code response}  — 最终响应</li>
 *   <li>{@code done}      — 管道完成</li>
 *   <li>{@code error}     — 错误</li>
 *   <li>{@code blocked}   — 安全检查阻止</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/oag")
public class OagController {

    private static final Logger log = LoggerFactory.getLogger(OagController.class);

    /** SSE 超时（毫秒） */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    @Autowired
    private OagPipelineEngine pipelineEngine;

    // ═══════════════════════════════════════════════════════════════
    //  1. POST /api/v1/oag/chat — 非流式对话（Map 入参，向后兼容）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 非流式 OAG 管道对话（向后兼容 Map 入参版本）。
     *
     * <p>保留 {@code Map<String, Object>} 入参签名，响应体改为
     * {@code ApiResponse<OagChatResponse>}（强类型外层包装）。</p>
     */
    @PostMapping("/chat")
    public ApiResponse<OagChatResponse> chat(@RequestBody Map<String, Object> body) {
        try {
            // 参数校验
            String message = (String) body.get("message");
            if (message == null || message.isBlank()) {
                return ApiResponse.badRequest("message 不能为空");
            }

            String userId = getStringOrDefault(body, "userId", "anonymous");
            String tenantId = getStringOrDefault(body, "tenantId", "default");

            // 执行管道
            OagPipelineContext ctx = pipelineEngine.run(message, body, userId, tenantId);

            // 构建强类型响应
            return ApiResponse.success(OagChatResponse.from(ctx));

        } catch (Exception e) {
            log.error("[OAG] 非流式对话失败", e);
            return ApiResponse.internalError("OAG 管道失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. POST /api/v1/oag/chat/v2 — 非流式对话（强类型 DTO 入参）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 非流式 OAG 管道对话（强类型 DTO 入参版本）。
     *
     * <p>使用 {@link OagChatRequest} 作为 {@code @RequestBody}，
     * 提供 Bean Validation 校验（{@code @NotBlank} on message）。</p>
     *
     * @param req 强类型请求
     * @return 强类型响应
     */
    @PostMapping("/chat/v2")
    public ApiResponse<OagChatResponse> chatV2(@Valid @RequestBody OagChatRequest req) {
        try {
            String userId = req.getUserId() != null ? req.getUserId() : "anonymous";
            String tenantId = req.getTenantId() != null ? req.getTenantId() : "default";

            // 执行管道（DTO 转为 Map 兼容 engine 入参）
            OagPipelineContext ctx = pipelineEngine.run(req.getMessage(), req.toMap(), userId, tenantId);

            // 如果外部传入了 sessionId，覆盖内部生成的
            OagChatResponse resp = OagChatResponse.from(ctx);
            if (req.getSessionId() != null && !req.getSessionId().isBlank()) {
                resp.setSessionId(req.getSessionId());
            }

            return ApiResponse.success(resp);

        } catch (Exception e) {
            log.error("[OAG] 非流式对话(v2)失败", e);
            return ApiResponse.internalError("OAG 管道失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. POST /api/v1/oag/chat/stream — SSE 流式
    // ═══════════════════════════════════════════════════════════════

    /**
     * SSE 流式 OAG 管道对话。
     *
     * <p>保留 Map 入参（兼容性优先），每个 SSE 事件 payload 改为
     * {@link OagStepEvent#toMap()} 的强类型序列化结果。
     * 事件名保持 {@code node/response/done/error/blocked} 不变。</p>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        CompletableFuture.runAsync(() -> {
            try {
                String message = (String) body.get("message");
                if (message == null || message.isBlank()) {
                    sendEvent(emitter, "error",
                            OagStepEvent.error(null, "FAILED", 0, null, "message 不能为空").toMap());
                    emitter.complete();
                    return;
                }

                String userId = getStringOrDefault(body, "userId", "anonymous");
                String tenantId = getStringOrDefault(body, "tenantId", "default");

                // 执行管道（带 SSE 回调 — 强类型 OagStepEvent）
                OagPipelineContext ctx = pipelineEngine.run(message, body, userId, tenantId,
                        event -> {
                            try {
                                // 将管道事件 Map 转为 OagStepEvent 再序列化
                                OagStepEvent stepEvent = toStepEvent(event);
                                sendEvent(emitter, "node", stepEvent.toMap());
                            } catch (IOException e) {
                                log.warn("[OAG] SSE 推送节点事件失败", e);
                            }
                        });

                // 推送最终响应
                if ("COMPLETED".equals(ctx.getStatus()) && ctx.getFinalResponse() != null) {
                    OagChatResponse resp = OagChatResponse.from(ctx);
                    Map<String, Object> respData = new LinkedHashMap<>();
                    respData.put("content", resp.getContent());
                    respData.put("traceId", resp.getTraceId());
                    sendEvent(emitter, "response", respData);
                } else if ("BLOCKED".equals(ctx.getStatus())) {
                    OagStepEvent blockedEvent = OagStepEvent.nodeDone(null, "BLOCKED", 0,
                            ctx.getTraceId(), ctx.getIntent(), ctx.isSecurityPassed());
                    blockedEvent.setMessage(ctx.getSecurityBlockReason() != null
                            ? ctx.getSecurityBlockReason() : "安全检查不通过");
                    sendEvent(emitter, "blocked", blockedEvent.toMap());
                } else if ("FAILED".equals(ctx.getStatus())) {
                    OagStepEvent errorEvent = OagStepEvent.error("Pipeline", "FAILED", ctx.getElapsedMs(),
                            ctx.getTraceId(), ctx.getErrorMessage() != null
                                    ? ctx.getErrorMessage() : "未知错误");
                    sendEvent(emitter, "error", errorEvent.toMap());
                }

                // 推送 done 事件
                OagStepEvent doneEvent = OagStepEvent.nodeDone(null, ctx.getStatus(),
                        ctx.getElapsedMs(), ctx.getTraceId(),
                        ctx.getIntent(), ctx.isSecurityPassed());
                sendEvent(emitter, "done", doneEvent.toMap());

                emitter.complete();

            } catch (Exception e) {
                log.error("[OAG] SSE 流式对话失败", e);
                try {
                    sendEvent(emitter, "error",
                            OagStepEvent.error(null, "FAILED", 0, null,
                                    "OAG 管道失败: " + e.getMessage()).toMap());
                } catch (Exception ignored) {
                    // emitter may already be closed
                }
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> log.debug("[OAG] SSE stream completed"));
        emitter.onTimeout(() -> log.warn("[OAG] SSE stream timed out after {}ms", SSE_TIMEOUT_MS));
        emitter.onError(ex -> log.error("[OAG] SSE stream error", ex));

        return emitter;
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. GET /api/v1/oag/chat/health — 健康检查
    // ═══════════════════════════════════════════════════════════════

    /**
     * OAG 管道健康检查。
     */
    @GetMapping("/chat/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("pipeline", "OAG 8-step DAG");
        data.put("nodes", new String[]{
                "IntentClassifier", "ContextLoader", "QueryRewriter",
                "SecurityChecker", "KnowledgeRetriever", "ReasoningEngine",
                "ResponseCompiler", "AuditLogger"
        });
        data.put("endpoints", new String[]{
                "POST /api/v1/oag/chat (Map)",
                "POST /api/v1/oag/chat/v2 (DTO)",
                "POST /api/v1/oag/chat/stream (SSE)",
                "GET  /api/v1/oag/chat/health"
        });
        return ApiResponse.success(data);
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有工具方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从管道事件 Map 构建 OagStepEvent。
     *
     * @param event 管道引擎传入的事件 Map（来自 OagPipelineContext.toSummary() + node/nodeElapsedMs）
     * @return 强类型步骤事件
     */
    @SuppressWarnings("unchecked")
    private OagStepEvent toStepEvent(Map<String, Object> event) {
        String node = (String) event.get("node");
        String traceId = (String) event.get("traceId");
        String intent = (String) event.get("intent");
        Object elapsed = event.get("nodeElapsedMs");
        long ms = elapsed instanceof Number n ? n.longValue() : 0L;
        boolean securityPassed = Boolean.TRUE.equals(event.get("securityPassed"));
        String status = "DONE";

        return OagStepEvent.nodeDone(node, status, ms, traceId, intent, securityPassed);
    }

    /**
     * 从 Map 中安全获取 String 值，缺失时返回默认值。
     */
    private static String getStringOrDefault(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val instanceof String s && !s.isBlank() ? s : defaultVal;
    }

    /**
     * 发送 SSE 事件。
     *
     * @param emitter     SSE 发射器
     * @param eventName   事件名（node/response/done/error/blocked）
     * @param eventPayload JSON 序列化的 Map payload
     * @throws IOException 发送失败
     */
    private void sendEvent(SseEmitter emitter, String eventName, Map<String, Object> eventPayload)
            throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(eventPayload, MediaType.APPLICATION_JSON));
    }
}
