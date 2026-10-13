package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunEventDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunEventDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Run 进度 SSE 实流通道（详细设计-10 §5.2 #7 streamRunEvents 的补债新增映射，F10-16）。
 *
 * <p><b>与既有 #7 JSON 面的关系（只增不改）</b>：{@link WAgentApiPaths#RUN_EVENTS_SSE}
 * （P-4 未就绪时的 502 {@code E-WA-DEP + fallback=polling} 降级出口）保持原样；
 * 本控制器新增 {@link WAgentApiPaths#RUN_EVENTS_SSE_STREAM} =
 * {@code GET /api/v1/wagent/runs/{runId}/events/stream}，{@code text/event-stream} 正流，
 * 以 {@code SseEmitter} + 轮询包装消费 append-only 引用型事件表
 * {@code ecos_ai.ecos_wagent_run_event}（V235，{@code idx_wagent_event_run(run_id, occur_time)} 正序回放）。</p>
 *
 * <p><b>语义边界（无假真理）</b>：P-4 跨进程事件总线（runtime-event Outbox）未接线 ⇒ 本通道
 * 为<b>表轮询回放形态</b>（首帧 {@code stream_mode=table-polling-replay} 如实标注），
 * 毫秒级实时推送待 F10-16 事件源切换；载荷只出 {@code payload_ref_text} 引用（§6.3 禁正文）。
 * {@code Last-Event-ID} 续传按事件 id 定位后取其后序；Run 进入五终态发
 * {@code run_terminal} 帧后正常收流。超时/断连由 {@code onTimeout/onError/onCompletion}
 * 回收轮询任务（禁裸线程泄漏）。</p>
 */
@RestController
public class WAgentRunEventStreamController {

    /** 流总超时（与同步通道 60s 预算同口径，§5.2 #5 注）。 */
    static final long SSE_TIMEOUT_MS = 60_000L;
    /** 表轮询间隔（回放形态缺省值，事件源接线后由推送取代）。 */
    static final long POLL_INTERVAL_MS = 1_000L;
    /** 流形态标注单源（F10-16 P-4 未就绪的诚实降级标记）。 */
    static final String STREAM_MODE = "table-polling-replay#P-4(runtime-event 未接线, F10-16)";

    private static final Logger log = LoggerFactory.getLogger(WAgentRunEventStreamController.class);

    private final WAgentRunDao runDao;
    private final WAgentRunEventDao runEventDao;
    private final WAgentIdentityResolver identityResolver;
    /** Spring 托管线程池（applicationTaskExecutor）：轮询泵禁裸线程/裸 CompletableFuture（SelfScheduleArchTest 红线）。 */
    private final TaskExecutor taskExecutor;

    public WAgentRunEventStreamController(WAgentRunDao runDao,
                                          WAgentRunEventDao runEventDao,
                                          WAgentIdentityResolver identityResolver,
                                          TaskExecutor taskExecutor) {
        this.runDao = runDao;
        this.runEventDao = runEventDao;
        this.identityResolver = identityResolver;
        this.taskExecutor = taskExecutor;
    }

    /** #7 新增 SSE 映射：状态 + 时间线事件流（Last-Event-ID 续传）。 */
    @GetMapping(value = WAgentApiPaths.RUN_EVENTS_SSE_STREAM,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRunEvents(
            @PathVariable(WAgentApiPaths.P_RUN_ID) String runId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        // 同步段 fail-fast：认证 → 存在性/租户校验（掩 404 防枚举）→ 才建流
        Identity identity = identityResolver.require();
        WAgentRunDO run = runDao.findById(runId)
                .filter(r -> identity.tenantId().equals(r.getTenantId()))
                .orElseThrow(WAgentOrchestrationException::notFound);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean aborted = new AtomicBoolean(false);
        emitter.onCompletion(() -> aborted.set(true));
        emitter.onTimeout(() -> {
            aborted.set(true);
            emitter.complete();
        });
        emitter.onError(e -> aborted.set(true));

        taskExecutor.execute(() -> pump(run.getId(), lastEventId, emitter, aborted));
        return emitter;
    }

    /** 轮询泵：首帧能力标注 → 续传定位回放存量 → 增量轮询至终态/超时/断连。 */
    private void pump(String runId, String lastEventId, SseEmitter emitter, AtomicBoolean aborted) {
        try {
            send(emitter, "stream_ready", Map.of(
                    "run_id", runId,
                    "stream_mode", STREAM_MODE,
                    "payload_kind", "reference-only(§6.3 禁正文)"));

            List<WAgentRunEventDO> all = runEventDao.findByRunId(runId);
            int start = resumeIndex(all, lastEventId);
            for (int i = start; i < all.size() && !aborted.get(); i++) {
                sendEventFrame(emitter, all.get(i));
            }

            long seenCount = Math.max(start, all.size());
            long deadline = System.currentTimeMillis() + SSE_TIMEOUT_MS;
            while (!aborted.get() && System.currentTimeMillis() < deadline) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Thread.sleep(POLL_INTERVAL_MS);
                if (aborted.get()) {
                    return;
                }
                List<WAgentRunEventDO> fresh = runEventDao.findByRunId(runId);
                for (int i = (int) seenCount; i < fresh.size() && !aborted.get(); i++) {
                    sendEventFrame(emitter, fresh.get(i));
                }
                seenCount = fresh.size();

                String status = runDao.findById(runId).map(WAgentRunDO::getStatus).orElse("");
                if (isTerminal(status)) {
                    send(emitter, "run_terminal", Map.of("run_id", runId, "status", status));
                    emitter.complete();
                    return;
                }
            }
            if (!aborted.get()) {
                // 超时收流：如实发超时提示帧后 complete（非错误，SSE 常规续接语义——客户端可带 Last-Event-ID 重连）
                send(emitter, "stream_timeout", Map.of("run_id", runId, "timeout_ms", SSE_TIMEOUT_MS));
                emitter.complete();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[wagent-sse] 轮询泵被中断 runId={}", runId);
        } catch (IOException e) {
            // 客户端断连：常规回收，不打错误堆栈
            log.debug("[wagent-sse] 客户端断连，回收轮询 runId={}", runId);
            emitter.complete();
        } catch (RuntimeException e) {
            log.error("[wagent-sse] 事件泵异常 runId={}", runId, e);
            try {
                send(emitter, "stream_error", Map.of("run_id", runId,
                        "message", "进度流暂时不可用（诊断线索已落服务端日志，禁外泄原文）"));
            } catch (IOException | RuntimeException ignored) {
                // 断连竞态：emitter 已关闭时放弃补帧
            }
            emitter.complete();
        }
    }

    /** Last-Event-ID 续传定位：命中 id ⇒ 其后序；未命中（伪造/已归档）⇒ 全量回放（无害只读）。 */
    static int resumeIndex(List<WAgentRunEventDO> events, String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0;
        }
        for (int i = 0; i < events.size(); i++) {
            if (lastEventId.trim().equals(events.get(i).getId())) {
                return i + 1;
            }
        }
        return 0;
    }

    private void sendEventFrame(SseEmitter emitter, WAgentRunEventDO event) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", event.getId());
        payload.put("run_id", event.getRunId());
        payload.put("step_id", event.getStepId());
        payload.put("event_type", event.getEventType());
        payload.put("payload_ref_text", event.getPayloadRefText());
        payload.put("occur_time", event.getOccurTime() == null ? null : event.getOccurTime().toString());
        emitter.send(SseEmitter.event()
                .id(event.getId())
                .name(event.getEventType() == null ? "run_event" : event.getEventType())
                .data(payload, MediaType.APPLICATION_JSON));
    }

    private void send(SseEmitter emitter, String name, Map<String, Object> data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
    }

    /** 五终态判定单源（{@link RunStatus#isTerminal()}；未知状态串 ⇒ 非终态，保守持续供流）。 */
    static boolean isTerminal(String rawStatus) {
        if (rawStatus == null) {
            return false;
        }
        for (RunStatus s : RunStatus.values()) {
            if (s.value().equals(rawStatus.trim().toLowerCase(java.util.Locale.ROOT))) {
                return s.isTerminal();
            }
        }
        return false;
    }
}
