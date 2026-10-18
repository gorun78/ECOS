package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.stream.SseFrameContract;
import com.chinacreator.gzcm.engine.ai.config.RunEventStreamExecutorConfig;
import com.chinacreator.gzcm.engine.ai.stream.AgentLoopSseFrames;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.DegradeCode;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunEventDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunStepDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunEventDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunStepDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.ReadinessCheckCode;
import com.chinacreator.gzcm.engine.ai.wagent.stream.WAgentSseFrames;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 * <p><b>#143/B5-5b 的帧契约收口（D-B5-8）</b>：出帧一律经 {@link WAgentSseFrames} →
 * {@link SseFrameContract} 的 W 分表，帧名固定为 §10.3.6 八帧
 * （{@code meta/step/token/delta/citation/degrade/error/done}）＋一帧通道补充帧
 * {@code milestone}。改前的自有词表（{@code stream_ready/<裸 event_type>/run_terminal/
 * stream_timeout/stream_error}）作废。</p>
 *
 * <p><b>八帧中本批只出五帧（无假真理，逐帧登记）</b>：{@code token/delta/citation} 在
 * V235/V234 上<b>零生产者</b>（LLM 逐段叙述与引用块不落 run 事件表），发一帧即伪造一帧，
 * 故本流不发；{@code step} 只有结束态（编排器不写 step-start）；{@code degrade} 的真值来自
 * V230 {@code degradations_json}（Readiness 判档写入）＋本通道自身的 {@code DG-M1}。
 * 缺帧位与缺生产者的差距另见 D-B5-15 起的登记。</p>
 *
 * <p><b>语义边界</b>：P-4 跨进程事件总线未接线 ⇒ 本通道为<b>表轮询回放形态</b>
 * （首帧 {@code meta.streamMode} 如实标注 + 头部 {@code degrade(DG-M1)}），
 * 载荷只出引用/身份位、不出 payload 正文（§6.3）。{@code Last-Event-ID} 续传按事件 id
 * 定位后取其后序；Run 进入五终态发 {@code done}（失败态前置 {@code error}）后正常收流。
 * <b>流预算耗尽而 Run 未终态 ⇒ 只发 {@code milestone(stream-timeout)} 后收流，不发
 * {@code done}</b>：§10.3.6 要求 done 的 status 为终态，用非终态凑末帧即冒充终态。</p>
 */
@RestController
public class WAgentRunEventStreamController {

    /** 流总超时（与同步通道 60s 预算同口径，§5.2 #5 注）。 */
    static final long SSE_TIMEOUT_MS = 60_000L;
    /** 表轮询间隔（回放形态缺省值，事件源接线后由推送取代）。 */
    static final long POLL_INTERVAL_MS = 1_000L;
    /** 流形态标注单源（F10-16 P-4 未就绪的诚实降级标记）。 */
    static final String STREAM_MODE = "table-polling-replay#P-4(runtime-event 未接线, F10-16)";
    /** 载荷形态标注单源：V235 列存的是内联 JSON 而非引用 ⇒ 承流面一律不出正文。 */
    static final String PAYLOAD_KIND = "withheld-inline-json(§6.3 引用型, D-B5-17)";
    /** 流预算耗尽的非终态收流标记（走 milestone，不冒充 done 终态）。 */
    static final String STREAM_TIMEOUT_MILESTONE = "stream-timeout";
    /** attempt 列缺失时的索引哨兵档（只用于归档行，禁当观测值抛进帧面）。 */
    private static final int MISSING_ATTEMPT = -1;

    private static final Logger log = LoggerFactory.getLogger(WAgentRunEventStreamController.class);

    private final WAgentRunDao runDao;
    private final WAgentRunEventDao runEventDao;
    private final WAgentRunStepDao runStepDao;
    private final WAgentReadinessDao readinessDao;
    private final WAgentIdentityResolver identityResolver;
    /**
     * W 域 SSE 轮询泵命名线程池（Bean 名单源 = {@code RunEventStreamExecutorConfig}，模块级底座提供）：
     * 轮询泵禁裸线程/裸 CompletableFuture（SelfScheduleArchTest 红线）。<b>必须按名注入</b>——gateway
     * 单体聚合面存在 5 个 {@code TaskExecutor} 候选且无 {@code @Primary}，按类型注入必歧义
     * （2026-10-16 实测 gateway 启动失败，详设-10 复盘批登记）。
     */
    private final TaskExecutor taskExecutor;

    /** 只读解析 V235/V230 的 JSON 语义 TEXT 列（不落库、不改写，故无需 Spring 托管的编解码器）。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流预算（ms）。默认与同步通道 60s 同口径（§5.2 #5 注）。
     *
     * <p>暴露为可短接的字段只为一条用例服务：<b>预算耗尽而 Run 未终态 ⇒ 只发
     * {@code milestone(stream-timeout)}、不发 {@code done}</b>。该形状若不可测，
     * 「末帧必发」就会被实现成"任何收流路径都补一帧 done"（即拿非终态冒充终态）。
     * 测试只缩短等待，不改任何帧名/键面/顺序 ⇒ 契约面没有为测试让路的分支。</p>
     */
    long streamBudgetMs = SSE_TIMEOUT_MS;

    public WAgentRunEventStreamController(WAgentRunDao runDao,
                                          WAgentRunEventDao runEventDao,
                                          WAgentRunStepDao runStepDao,
                                          WAgentReadinessDao readinessDao,
                                          WAgentIdentityResolver identityResolver,
                                          @Qualifier(RunEventStreamExecutorConfig.RUN_EVENT_STREAM_EXECUTOR)
                                          TaskExecutor taskExecutor) {
        this.runDao = runDao;
        this.runEventDao = runEventDao;
        this.runStepDao = runStepDao;
        this.readinessDao = readinessDao;
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

        // traceId 必须在请求线程取：泵线程 MDC 为空，迟取会另生成一条 ID ⇒ 一屏两个诊断码
        String traceId = AiTraceSupport.currentTraceId();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean aborted = new AtomicBoolean(false);
        emitter.onCompletion(() -> aborted.set(true));
        emitter.onTimeout(() -> {
            aborted.set(true);
            emitter.complete();
        });
        emitter.onError(e -> aborted.set(true));

        WAgentSseFrames frames = new WAgentSseFrames(traceId, sinkOf(emitter));
        taskExecutor.execute(() -> pump(run.getId(), lastEventId, frames, emitter, aborted));
        return emitter;
    }

    /**
     * 出帧汇：帧载荷里的 {@code eventId} 同时充作 SSE 线格式的 {@code id:} 位。
     *
     * <p>续传依赖它：客户端带 {@code Last-Event-ID} 重连时按 run_event 行 id 定位后序，
     * 故只有带行 id 的帧（step/milestone）可被续传锚定；meta/degrade/done 是通道帧，
     * 不写 id 行（写了反而让客户端把非事件帧当成续传锚点）。</p>
     */
    private static AgentLoopSseFrames.SseFrameSink sinkOf(SseEmitter emitter) {
        return (event, data) -> {
            Object eventId = data.get(SseFrameContract.KEY_EVENT_ID);
            SseEmitter.SseEventBuilder builder = SseEmitter.event().name(event)
                    .data(data, MediaType.APPLICATION_JSON);
            if (eventId != null) {
                builder.id(String.valueOf(eventId));
            }
            emitter.send(builder);
        };
    }

    /** 轮询泵：首帧 meta → degrade 头 → 续传定位回放存量 → 增量轮询至终态/超时/断连。 */
    private void pump(String runId, String lastEventId, WAgentSseFrames frames,
                      SseEmitter emitter, AtomicBoolean aborted) {
        try {
            WAgentRunDO run = runDao.findById(runId).orElse(null);
            if (run == null) {
                // 同步段已校验存在性；此处消失=并发删除，收流即可（禁对不存在的 run 出假终态）
                emitter.complete();
                return;
            }
            List<String> degradationCodes = degradationsOf(run);
            frames.meta(run.getId(), run.getPlanSource(), parseJson(run.getBudgetJson()),
                    STREAM_MODE, PAYLOAD_KIND);
            emitDegrades(frames, degradationCodes);

            Map<String, TreeMap<Integer, WAgentRunStepDO>> stepsByRunKey = loadSteps(runId);
            List<WAgentRunEventDO> all = runEventDao.findByRunId(runId);
            int start = resumeIndex(all, lastEventId);
            for (int i = start; i < all.size() && !aborted.get(); i++) {
                emit(frames, all.get(i), stepsByRunKey);
            }
            if (closeIfTerminal(runId, degradationCodes, frames, aborted)) {
                finish(emitter, frames);
                return;
            }

            long seenCount = Math.max(start, all.size());
            long deadline = System.currentTimeMillis() + streamBudgetMs;
            while (!aborted.get() && System.currentTimeMillis() < deadline) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Thread.sleep(POLL_INTERVAL_MS);
                if (aborted.get()) {
                    return;
                }
                stepsByRunKey = loadSteps(runId);
                List<WAgentRunEventDO> fresh = runEventDao.findByRunId(runId);
                for (int i = (int) seenCount; i < fresh.size() && !aborted.get(); i++) {
                    emit(frames, fresh.get(i), stepsByRunKey);
                }
                seenCount = fresh.size();
                if (closeIfTerminal(runId, degradationCodes, frames, aborted)) {
                    finish(emitter, frames);
                    return;
                }
            }
            if (!aborted.get()) {
                // 超时收流：Run 未终态 ⇒ 不发 done（§10.3.6 末帧 status 必须是终态，凑即冒充）
                frames.milestone(null, STREAM_TIMEOUT_MILESTONE, null, PAYLOAD_KIND);
            }
            finish(emitter, frames);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[wagent-sse] 轮询泵被中断 runId={}", runId);
            finish(emitter, frames);
        } catch (IOException e) {
            // 客户端断连：常规回收，不打错误堆栈
            log.debug("[wagent-sse] 客户端断连，回收轮询 runId={}", runId);
            finish(emitter, frames);
        } catch (RuntimeException e) {
            log.error("[wagent-sse] 事件泵异常 runId={}", runId, e);
            try {
                frames.error(null, "进度流暂时不可用（诊断线索已落服务端日志，禁外泄原文）", null);
                // 补一帧 done 只在真实读到终态时；泵自身缺陷不等于 Run 已终态，读不到就不冒充
                closeTerminalRunIfAny(runId, degradationCodesOf(runId), frames);
            } catch (IOException | RuntimeException ignored) {
                // 断连竞态：emitter 已关闭时放弃补帧
            }
            finish(emitter, frames);
        }
    }

    /**
     * 收流：只负责 {@code complete()}，且吞掉"已关闭"竞态异常。
     *
     * <p>每条终止路径（终态 done / 超时 milestone / 中断 / 断连 / 泵异常）都必须收流，
     * 否则客户端要等满 {@value #SSE_TIMEOUT_MS}ms 才看到流结束——这是原实现的实际行为，
     * 也是本批登记的另一条缺陷（终态后不收流）。</p>
     */
    private static void finish(SseEmitter emitter, WAgentSseFrames frames) {
        try {
            emitter.complete();
        } catch (RuntimeException alreadyClosed) {
            log.debug("[wagent-sse] 收流时通道已关闭 traceId={}", frames.traceId());
        }
    }

    /** 终态收流：失败态先补 error 帧（§10.3.6 帧 7 紧接末帧），再出 done。 */
    private boolean closeIfTerminal(String runId, List<String> degradationCodes,
                                    WAgentSseFrames frames, AtomicBoolean aborted) throws IOException {
        if (aborted.get()) {
            return true;
        }
        WAgentRunDO run = runDao.findById(runId).orElse(null);
        String status = run == null ? "" : run.getStatus();
        if (!isTerminal(status)) {
            return false;
        }
        if (RunStatus.FAILED.value().equals(trimLower(status))) {
            emitFailureFrame(runId, frames);
        }
        frames.done(runId, status, gradeOf(run), degradationCodes);
        return true;
    }

    /** 泵异常分支专用：重新读 run，只有确实已进终态才补末帧（缺帧好于冒充终态）。 */
    private boolean closeTerminalRunIfAny(String runId, List<String> degradationCodes,
                                          WAgentSseFrames frames) throws IOException {
        WAgentRunDO run = runDao.findById(runId).orElse(null);
        String status = run == null ? null : run.getStatus();
        if (status == null || !isTerminal(status)) {
            return false;
        }
        frames.done(runId, status, gradeOf(run), degradationCodes);
        return true;
    }

    /** 二次读降级位（泵异常时 run 对象可能已不可达，改从库里重取）。 */
    private List<String> degradationCodesOf(String runId) {
        return runDao.findById(runId).map(this::degradationsOf).orElseGet(List::of);
    }

    /** 失败末帧的错误位：取最后一条 {@code RunFailed} 的 code（V235 真值），分类经 W 域错误码单源。 */
    private void emitFailureFrame(String runId, WAgentSseFrames frames) throws IOException {
        String wireCode = null;
        String service = null;
        for (WAgentRunEventDO event : runEventDao.findByRunId(runId)) {
            if (EventTypes.Agent.RUN_FAILED.equals(event.getEventType())) {
                wireCode = textOrNull(payloadField(event, "code"));
                service = textOrNull(payloadField(event, "service"));
            }
        }
        Boolean retryable = null;
        String message = null;
        if (wireCode != null) {
            try {
                WAgentErrorCode code = WAgentErrorCodes.fromWireCode(wireCode);
                retryable = code.clientRetryable();
                // 只有 E-WA-DEP 模板消费 {service}；其余模板忽略该位，传值无害且不进帧
                message = code.renderMessage(frames.traceId(), service);
            } catch (IllegalArgumentException unregistered) {
                // 码面漂移：只如实出码，不替它编一个可重试性（缺位好于假造）
                log.warn("[wagent-sse] RunFailed 携带未注册错误码，错误帧只出码不出重试性 runId={}", runId);
                message = "运行失败（诊断码 " + SseFrameContract.textOrAbsent(frames.traceId()) + "）";
            }
        }
        frames.error(wireCode, message, retryable);
    }

    /** 逐帧分发：步骤级事件出 step 帧，其余生命周期事件出 milestone 补充帧（八帧无对应帧位）。 */
    private void emit(WAgentSseFrames frames, WAgentRunEventDO event,
                      Map<String, TreeMap<Integer, WAgentRunStepDO>> stepsByRunKey) throws IOException {
        String occurTime = event.getOccurTime() == null ? null : event.getOccurTime().toString();
        if (!isStepScopedEvent(event.getEventType())) {
            frames.milestone(event.getId(), event.getEventType(), occurTime, PAYLOAD_KIND);
            return;
        }
        String stepKey = textOrNull(payloadField(event, "step_key"));
        Integer attempt = attemptOf(payloadField(event, "attempt"));
        WAgentRunStepDO step = resolveStep(stepsByRunKey.get(stepKey), attempt);
        frames.step(event.getId(), stepKey,
                step == null ? null : step.getStepType(),
                step == null ? null : step.getToolName(),
                // attempt 一律取事件自己声明的那一次（事件未带才回落行上的档），禁让状态与档错位
                attempt != null ? attempt : (step == null ? null : realAttemptOf(step)),
                step == null ? null : step.getStatus(),
                step == null ? null : step.getPolicyEffect());
    }

    /**
     * 取该事件对应的那一次 attempt 档。
     *
     * <p>事件带 attempt 但 V234 上没有该档 ⇒ 返 {@code null}（缺位），<b>不回落"最新一次"</b>：
     * 把 attempt 2 的状态贴在 attempt 1 的事件上，等于伪造一次未发生过的重试结果。
     * 事件未带 attempt（历史行、编排器改版前的写入）才取最高档。</p>
     */
    private static WAgentRunStepDO resolveStep(TreeMap<Integer, WAgentRunStepDO> attempts, Integer attempt) {
        if (attempts == null || attempts.isEmpty()) {
            return null;
        }
        if (attempt != null) {
            return attempts.get(attempt);
        }
        return attempts.get(attempts.lastKey());
    }

    /** 事件载荷的 attempt 位（非数值 ⇒ 视为未带，禁猜一个档）。 */
    private static Integer attemptOf(Object raw) {
        return raw instanceof Number number ? number.intValue() : null;
    }

    /** 行上真实 attempt 档；哨兵值（{@code null} 列）不外抛，落键缺位。 */
    private static Integer realAttemptOf(WAgentRunStepDO step) {
        return step.getAttempt() == null || step.getAttempt() < 0 ? null : step.getAttempt();
    }

    /**
     * 步骤级事件判定单源（V235 实际写入面只有这三类带 {@code step_key}）。
     *
     * <p>其余 8 类是 Run 生命周期里程碑（{@code RunCreated}/{@code PlanGenerated}/
     * {@code DecisionOptionsReady} 等），塞进 {@code step} 就是把非步骤事件冒充为步骤，
     * 故走 {@code milestone}。事件名一律引 {@link EventTypes.Agent} 常量，禁在本类抄字面量。</p>
     */
    static boolean isStepScopedEvent(String eventType) {
        return EventTypes.Agent.STEP_COMPLETED.equals(eventType)
                || EventTypes.Agent.STEP_FAILED.equals(eventType)
                || EventTypes.Agent.POLICY_DENIED.equals(eventType);
    }

    /**
     * 生效降级码 = V230 评估头 ∪ V233 run 行 ∪ 本通道 DG-M1。
     *
     * <p>两个真载体都读：§10.3.6 帧 8 的 {@code degradations[]} 与帧 6 同源，编排器写侧
     * 目前只有 {@code ReadinessService} 落 V230 一条腿（{@code WAgentRunDO.degradationsJson}
     * 的 setter 全仓零调用 ⇒ run 行恒空，已登记 D-B5-16）。读侧保持两条腿是<b>契约要求</b>
     * 而非投机：写侧补上时承流面不需要再改一次，也就不会出现"库里有了、帧里还缺"的错位。</p>
     */
    List<String> degradationsOf(WAgentRunDO run) {
        Set<String> codes = new LinkedHashSet<>();
        collectRegistered(run.getDegradationsJson(), codes);
        if (run.getReadinessId() != null && !run.getReadinessId().isBlank()) {
            readinessDao.findById(run.getReadinessId())
                    .map(WAgentReadinessDO::getDegradationsJson)
                    .ifPresent(json -> collectRegistered(json, codes));
        }
        codes.add(DegradeCode.DG_M1.value());
        return List.copyOf(codes);
    }

    /** 只收白名单内的码（F10-10：配置可关不可开新，未登记码不进承流面）。 */
    private void collectRegistered(String json, Set<String> into) {
        for (Object item : asList(json)) {
            String code = textOrNull(item);
            if (code == null) {
                continue;
            }
            if (DegradeCode.ALL.contains(code)) {
                into.add(code);
            } else {
                log.warn("[wagent-sse] 降级码未登记在 DG 白名单，跳出不发帧: {}", code);
            }
        }
    }

    private static void emitDegrades(WAgentSseFrames frames, List<String> codes) throws IOException {
        for (String code : codes) {
            DegradeCode degrade = degradeOf(code);
            if (degrade == null) {
                continue;
            }
            frames.degrade(degrade.value(), appliesToOf(degrade), degrade.requiresAck(), degrade.description());
        }
    }

    private static DegradeCode degradeOf(String code) {
        for (DegradeCode value : DegradeCode.values()) {
            if (value.value().equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * {@code appliesTo} 的唯一可得来源 = 检查项单源上挂载该 DG 码的层位集。
     *
     * <p><b>DegradeCode 自身不带 applies_to 字段</b>（其 javadoc 声称带，实测无，已登记 D-B5-15），
     * 而 {@code ReadinessCheckCode} 逐条记录了"该项不达标挂哪个 DG 码"，层位由此可确定性反推。
     * 反推不出（无检查项挂载、如本通道的 DG-M1）就给显式范围词，禁留空冒充"适用于全部"。</p>
     */
    static String appliesToOf(DegradeCode code) {
        if (code == DegradeCode.DG_M1) {
            return "progress-channel";
        }
        Set<String> layers = new LinkedHashSet<>();
        for (ReadinessCheckCode check : ReadinessCheckCode.values()) {
            if (check.degradeCode() == code) {
                layers.add(check.layer().value());
            }
        }
        return layers.isEmpty() ? SseFrameContract.textOrAbsent(null) : String.join(",", layers);
    }

    /** 末帧 grade 位：只取评估头实测档，无评估即缺位（禁从 run 反推一个字母冒充评级）。 */
    private String gradeOf(WAgentRunDO run) {
        if (run == null || run.getReadinessId() == null || run.getReadinessId().isBlank()) {
            return null;
        }
        return readinessDao.findById(run.getReadinessId())
                .map(WAgentReadinessDO::getGrade)
                .orElse(null);
    }

    /**
     * 按 {@code (step_key, attempt)} 双键索引 V234 行（同 key 多 attempt 各自留档，禁并成一条状态）。
     *
     * <p>{@code attempt} 为 {@code null} 的脏行落到哨兵档 {@code -1}：仍可出状态，但不把
     * {@code -1} 当观测值抛给帧面（见 {@link #realAttemptOf}）。</p>
     */
    private Map<String, TreeMap<Integer, WAgentRunStepDO>> loadSteps(String runId) {
        Map<String, TreeMap<Integer, WAgentRunStepDO>> byKey = new LinkedHashMap<>();
        for (WAgentRunStepDO row : runStepDao.findByRunId(runId)) {
            if (row.getStepKey() == null) {
                continue;
            }
            byKey.computeIfAbsent(row.getStepKey(), k -> new TreeMap<>())
                    .put(row.getAttempt() == null ? MISSING_ATTEMPT : row.getAttempt(), row);
        }
        return byKey;
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

    private Object payloadField(WAgentRunEventDO event, String key) {
        Object node = parseJson(event.getPayloadRefText());
        return node instanceof Map<?, ?> map ? map.get(key) : null;
    }

    /** JSON 语义 TEXT 列的只读解析：解不出即返 null（缺位），禁在此造空对象冒充"解析成功"。 */
    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("[wagent-sse] JSON 列不可解析，按缺位出帧: {}", e.getOriginalMessage());
            return null;
        }
    }

    private List<Object> asList(String json) {
        Object parsed = parseJson(json);
        return parsed instanceof List<?> list ? new ArrayList<>(list) : List.of();
    }

    private static String textOrNull(Object value) {
        String text = value == null ? null : String.valueOf(value);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String trimLower(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 五终态判定单源（{@link RunStatus#isTerminal()}；未知状态串 ⇒ 非终态，保守持续供流）。 */
    static boolean isTerminal(String rawStatus) {
        String normalized = trimLower(rawStatus);
        for (RunStatus s : RunStatus.values()) {
            if (s.value().equals(normalized)) {
                return s.isTerminal();
            }
        }
        return false;
    }
}
