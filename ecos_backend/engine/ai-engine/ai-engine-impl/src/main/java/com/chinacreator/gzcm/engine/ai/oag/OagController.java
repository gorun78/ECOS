package com.chinacreator.gzcm.engine.ai.oag;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.stream.OagSseFrameContract;
import com.chinacreator.gzcm.engine.ai.oag.dto.OagChatRequest;
import com.chinacreator.gzcm.engine.ai.oag.dto.OagChatResponse;
import com.chinacreator.gzcm.engine.ai.oag.dto.OagStepEvent;
import com.chinacreator.gzcm.engine.ai.stream.AgentLoopSseFrames;
import com.chinacreator.gzcm.engine.ai.stream.OagSseFrames;
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
 * <p>SSE 事件词表与载荷键单一来源 = {@link OagSseFrameContract}（写出面 =
 * {@code stream/OagSseFrames}，禁在本类自拼事件名）：</p>
 * <ul>
 *   <li>{@code node}      — 每个节点完成时推送</li>
 *   <li>{@code response}  — 最终响应</li>
 *   <li>{@code done}      — 管道完成（末帧恒发）</li>
 *   <li>{@code error}     — 错误（至多一条且紧接 done 之前）</li>
 *   <li>{@code blocked}   — 安全检查阻止（拦截原因出 {@code reason} 键）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/oag")
public class OagController {

    private static final Logger log = LoggerFactory.getLogger(OagController.class);

    /** SSE 超时（毫秒） */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    /**
     * 管道异常的用户消息模板（D-B5-14：承流 error 帧永不携带异常原文）。
     *
     * <p>改前该帧写 {@code "OAG 管道失败: " + e.getMessage()}，而 {@code getMessage()}
     * 可能含数据源串、SQL 片段或第三方 SDK 报文——同 §9.3.3 对 error 帧的约束。
     * 原文一律入日志，帧里只出模板 + 诊断码。</p>
     */
    private static final String PIPELINE_FAILURE_TEMPLATE = "OAG 管道失败，请稍后重试（诊断码 {traceId}）";
    private static final String DIAGNOSTIC_PLACEHOLDER = "{traceId}";

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
            return ApiResponse.internalError("OAG 管道失败");
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
            return ApiResponse.internalError("OAG 管道失败");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. POST /api/v1/oag/chat/stream — SSE 流式
    // ═══════════════════════════════════════════════════════════════

    /**
     * SSE 流式 OAG 管道对话。
     *
     * <p>事件名与载荷键单一来源 = {@link OagSseFrameContract}，出帧面单一入口 =
     * {@link OagSseFrames}；本方法只负责 {@code SseEmitter} 装配与异步泵送，
     * 不再逐处 {@code sendEvent(emitter, "<字面量>", ...)}（D-B5-7 病灶即该形态：
     * {@code error}/{@code done} 与 agent-loop 七帧同字面量却不同载荷形）。</p>
     *
     * <p>末帧 {@code done} 恒发（含参数校验失败与管道抛异常两条腿）：SSE 无末帧时
     * 前端解析器只能靠连接关闭收尾，断线续传与"跑完什么都没显示"的判读都失去依据。</p>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        OagSseFrames frames = new OagSseFrames(sinkOf(emitter));

        CompletableFuture.runAsync(() -> {
            streamChannel(frames, body);
            emitter.complete();
        });

        emitter.onCompletion(() -> log.debug("[OAG] SSE stream completed, 已出帧={}", frames.emittedNames()));
        emitter.onTimeout(() -> log.warn("[OAG] SSE stream timed out after {}ms", SSE_TIMEOUT_MS));
        emitter.onError(ex -> log.error("[OAG] SSE stream error", ex));

        return emitter;
    }

    /**
     * 通道腿：真实出帧面 + 兜底错误帧（包内可见供入口驱动测试，同
     * {@code AgentLoopController#runStream} 的纪律——帧序要从发射面本身断出来）。
     *
     * <p>本方法<b>不抛</b>：管道异常一律转成 {@code error → done} 两帧，异常原文只入日志。
     * 改前异常腿走 {@code emitter.completeWithError(e)}，把失败以传输层异常形态甩给客户端，
     * 前端 fetch-stream 侧表现为流中断而非错误帧（承流面自带错误帧时不该再炸传输）。</p>
     */
    void streamChannel(OagSseFrames frames, Map<String, Object> body) {
        try {
            runStream(frames, body);
        } catch (Exception e) {
            log.error("[OAG] SSE 流式对话失败", e);
            try {
                frames.send(OagStepEvent.error(null, "FAILED", 0L, frames.traceId(),
                        PIPELINE_FAILURE_TEMPLATE.replace(DIAGNOSTIC_PLACEHOLDER, frames.traceId())));
                frames.done(OagStepEvent.done("FAILED", 0L, frames.traceId(), null, false));
            } catch (Exception ignored) {
                // 断连竞态：sink 已关闭时补帧必失败，放弃（异常原文已在上面入日志）
            }
        }
    }

    /** 出帧主体：参数校验 → 管道执行（节点进度帧）→ 终态帧 → 末帧。 */
    private void runStream(OagSseFrames frames, Map<String, Object> body) throws IOException {
        String message = (String) body.get("message");
        if (message == null || message.isBlank()) {
            frames.send(OagStepEvent.error(null, "FAILED", 0L, frames.traceId(), "message 不能为空"));
            frames.done(OagStepEvent.done("FAILED", 0L, frames.traceId(), null, false));
            return;
        }

        String userId = getStringOrDefault(body, "userId", "anonymous");
        String tenantId = getStringOrDefault(body, "tenantId", "default");

        OagPipelineContext ctx = pipelineEngine.run(message, body, userId, tenantId,
                event -> {
                    try {
                        frames.send(toStepEvent(event));
                    } catch (IOException e) {
                        log.warn("[OAG] SSE 推送节点事件失败", e);
                    }
                });

        frames.bindTraceId(ctx.getTraceId());
        String traceId = frames.traceId();

        if ("COMPLETED".equals(ctx.getStatus()) && ctx.getFinalResponse() != null) {
            OagChatResponse resp = OagChatResponse.from(ctx);
            frames.send(OagStepEvent.response(resp.getContent(), traceId));
        } else if ("BLOCKED".equals(ctx.getStatus())) {
            frames.send(OagStepEvent.blocked(ctx.getSecurityBlockReason() != null
                    ? ctx.getSecurityBlockReason() : "安全检查不通过", traceId,
                    ctx.getIntent(), ctx.isSecurityPassed()));
        } else if ("FAILED".equals(ctx.getStatus())) {
            frames.send(OagStepEvent.error("Pipeline", "FAILED", ctx.getElapsedMs(), traceId,
                    ctx.getErrorMessage() != null ? ctx.getErrorMessage() : "未知错误"));
        }

        frames.done(OagStepEvent.done(ctx.getStatus(), ctx.getElapsedMs(), traceId,
                ctx.getIntent(), ctx.isSecurityPassed()));
    }

    /** {@code SseEmitter} 桥：事件名与载荷都由写出器给定，本处只做线路装配。 */
    private static AgentLoopSseFrames.SseFrameSink sinkOf(SseEmitter emitter) {
        return (event, data) -> emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
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
}
