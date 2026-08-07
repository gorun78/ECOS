package com.chinacreator.gzcm.engine.ai.oag;

import com.chinacreator.gzcm.common.base.ApiResponse;
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
 * OAG Pipeline Controller — SSE 流式端点 + 非流式端点。
 *
 * <pre>
 *   POST /api/v1/oag/chat              — 非流式对话 (JSON)
 *   POST /api/v1/oag/chat/stream       — SSE 流式对话 (text/event-stream)
 *   POST /api/v1/oag/chat/health       — 管道健康检查
 * </pre>
 *
 * <p>SSE 事件类型：</p>
 * <ul>
 *   <li>{@code node}      — 每个节点完成时推送（含节点名、耗时、摘要）</li>
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
    //  1. POST /api/v1/oag/chat — 非流式对话
    // ═══════════════════════════════════════════════════════════════

    /**
     * 非流式 OAG 管道对话。
     *
     * <pre>
     * {
     *   "message": "查询今天的销售数据",
     *   "userId": "user-001",           // 必填
     *   "tenantId": "tenant-001",       // 必填
     *   "model": "deepseek-chat",       // 可选
     *   "temperature": 0.7,             // 可选
     *   "maxTokens": 4096,              // 可选
     *   "domain": "sales",              // 可选
     *   "language": "zh-CN"             // 可选
     * }
     * </pre>
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        try {
            // 参数校验
            String message = (String) body.get("message");
            if (message == null || message.isBlank()) {
                return ApiResponse.badRequest("message 不能为空");
            }

            String userId = (String) body.getOrDefault("userId", "anonymous");
            String tenantId = (String) body.getOrDefault("tenantId", "default");

            // 执行管道
            OagPipelineContext ctx = pipelineEngine.run(message, body, userId, tenantId);

            // 构建响应
            Map<String, Object> data = buildResponse(ctx);

            if ("COMPLETED".equals(ctx.getStatus())) {
                return ApiResponse.success(data);
            } else if ("BLOCKED".equals(ctx.getStatus())) {
                return ApiResponse.success(Map.of(
                        "blocked", true,
                        "reason", ctx.getSecurityBlockReason(),
                        "traceId", ctx.getTraceId()
                ));
            } else {
                return ApiResponse.internalError("管道执行失败: " + ctx.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("[OAG] 非流式对话失败", e);
            return ApiResponse.internalError("OAG 管道失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. POST /api/v1/oag/chat/stream — SSE 流式
    // ═══════════════════════════════════════════════════════════════

    /**
     * SSE 流式 OAG 管道对话。
     *
     * <p>事件流：</p>
     * <pre>
     *   event:node      → {"node":"IntentClassifier","status":"RUNNING","elapsedMs":5,...}
     *   event:node      → {"node":"ContextLoader",...}
     *   ...
     *   event:response  → {"content":"根据您的查询..."}
     *   event:done      → {"traceId":"oag-xxx","elapsedMs":1234,"status":"COMPLETED"}
     * </pre>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        CompletableFuture.runAsync(() -> {
            try {
                String message = (String) body.get("message");
                if (message == null || message.isBlank()) {
                    sendEvent(emitter, "error", Map.of("message", "message 不能为空"));
                    emitter.complete();
                    return;
                }

                String userId = (String) body.getOrDefault("userId", "anonymous");
                String tenantId = (String) body.getOrDefault("tenantId", "default");

                // 执行管道（带 SSE 回调）
                OagPipelineContext ctx = pipelineEngine.run(message, body, userId, tenantId,
                        event -> {
                            try {
                                sendEvent(emitter, "node", event);
                            } catch (IOException e) {
                                log.warn("[OAG] SSE 推送节点事件失败", e);
                            }
                        });

                // 推送最终响应
                if ("COMPLETED".equals(ctx.getStatus()) && ctx.getFinalResponse() != null) {
                    sendEvent(emitter, "response", Map.of(
                            "content", ctx.getFinalResponse(),
                            "traceId", ctx.getTraceId()
                    ));
                } else if ("BLOCKED".equals(ctx.getStatus())) {
                    sendEvent(emitter, "blocked", Map.of(
                            "reason", ctx.getSecurityBlockReason() != null
                                    ? ctx.getSecurityBlockReason() : "安全检查不通过",
                            "traceId", ctx.getTraceId()
                    ));
                } else if ("FAILED".equals(ctx.getStatus())) {
                    sendEvent(emitter, "error", Map.of(
                            "message", ctx.getErrorMessage() != null
                                    ? ctx.getErrorMessage() : "未知错误",
                            "traceId", ctx.getTraceId()
                    ));
                }

                // 推送 done 事件
                sendEvent(emitter, "done", Map.of(
                        "traceId", ctx.getTraceId(),
                        "sessionId", ctx.getSessionId(),
                        "elapsedMs", ctx.getElapsedMs(),
                        "status", ctx.getStatus(),
                        "intent", ctx.getIntent(),
                        "securityPassed", ctx.isSecurityPassed()
                ));

                emitter.complete();

            } catch (Exception e) {
                log.error("[OAG] SSE 流式对话失败", e);
                try {
                    sendEvent(emitter, "error", Map.of("message", "OAG 管道失败: " + e.getMessage()));
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
    //  3. GET /api/v1/oag/chat/health — 健康检查
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
                "POST /api/v1/oag/chat",
                "POST /api/v1/oag/chat/stream (SSE)",
                "GET  /api/v1/oag/chat/health"
        });
        return ApiResponse.success(data);
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有工具方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 构建非流式响应。
     */
    private Map<String, Object> buildResponse(OagPipelineContext ctx) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("traceId", ctx.getTraceId());
        data.put("sessionId", ctx.getSessionId());
        data.put("status", ctx.getStatus());
        data.put("intent", ctx.getIntent());
        data.put("securityPassed", ctx.isSecurityPassed());
        data.put("elapsedMs", ctx.getElapsedMs());

        if (ctx.getFinalResponse() != null) {
            data.put("content", ctx.getFinalResponse());
        }
        if (ctx.getErrorMessage() != null) {
            data.put("errorMessage", ctx.getErrorMessage());
        }
        if (ctx.getSecurityBlockReason() != null) {
            data.put("blockReason", ctx.getSecurityBlockReason());
        }

        // 推理元数据
        Map<String, Object> reasoning = ctx.getReasoningResult();
        if (reasoning != null) {
            data.put("model", reasoning.get("model"));
            data.put("tokensUsed", reasoning.get("tokensUsed"));
            data.put("reasoningLatencyMs", reasoning.get("latencyMs"));
        }

        return data;
    }

    /**
     * 发送 SSE 事件。
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(data, MediaType.APPLICATION_JSON));
    }
}
