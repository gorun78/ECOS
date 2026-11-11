package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.approval.ApprovalTokenService;
import com.chinacreator.gzcm.engine.ai.wagent.approval.WAgentApprovalScope;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrator;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrator.RunTimelineVO;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

/**
 * Run 端点（详细设计-10 §5.2 #5~#10 + 进度能力位，F10-11/F10-16）。
 *
 * <p><b>#5 双通道口径（诚实实现，禁伪异步）</b>：{@code Prefer: respond-async} ⇒
 * 只建 Run（幂等闸内）即 {@code 202 + run_id}；执行派发接驳属 #130（ITaskManagementService
 * 调度单源，禁 @Scheduled/裸线程池），故响应内明示 {@code execution_dispatch=pending}，
 * 进度经轮询面（#6 + progress-capability）消费。同步通道（无 Prefer 头）⇒ 建 Run 后同请求
 * 内推进管线（60s 预算到点转 {@code timed_out}，§5.3：禁把超时包成 500）。</p>
 *
 * <p><b>#7 SSE</b>：P-4（进度通道底座）未就绪 ⇒ 如实返回 502 {@code E-WA-DEP} +
 * {@code fallback=polling} + 降级标记 {@code DG-M1}（禁 200 伪流，F10-16）。</p>
 */
@RestController
public class WAgentRunController {

    /** RFC 7240 Prefer 头（值含 respond-async 即异步通道）。 */
    static final String HEADER_PREFER = "Prefer";
    static final String PREFER_RESPOND_ASYNC = "respond-async";
    /** 幂等键双通道：body.idempotencyKey 优先，缺省回落本头（§5.2 #5）。 */
    static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    /** SSE 未就绪降级标记（§5.2 表 DG-M1；与前端轮询通道约定同源）。 */
    static final String DEGRADE_CODE_POLLING = "DG-M1";

    private final WAgentOrchestrator orchestrator;
    private final WAgentIdentityResolver identityResolver;
    private final ApprovalTokenService approvalTokenService;

    public WAgentRunController(WAgentOrchestrator orchestrator, WAgentIdentityResolver identityResolver,
                               ApprovalTokenService approvalTokenService) {
        this.orchestrator = orchestrator;
        this.identityResolver = identityResolver;
        this.approvalTokenService = approvalTokenService;
    }

    /** #5 建 Run 请求体（automationCeiling 0~3，缺省 L1 fail-closed 默认）。 */
    public record RunStartRequest(String questionId, String idempotencyKey, Integer automationCeiling,
                                  String parentRunId, String previousRunId, String situationSnapshotJson) {
    }

    /** #8 awaiting_input 应答体（载荷只作快照引用，禁正文入事件；键面由调用方定义，故保持自由映射）。 */
    public record RunInputRequest(Map<String, Object> input) {
    }

    /** #9 审批体（approval_token 必填，凭证校验在编排面）。 */
    public record RunApprovalRequest(
            @JsonProperty("approvalToken") @JsonAlias("approval_token") String approvalToken) {
    }

    /** Run 基视图（8 键；旧 Map 逐键同名同序）。 */
    public record RunVO(@JsonProperty("run_id") String runId,
                        @JsonProperty("question_id") String questionId,
                        @JsonProperty("mission_id") String missionId,
                        @JsonProperty("status") String status,
                        @JsonProperty("plan_source") String planSource,
                        @JsonProperty("automation_ceiling") Integer automationCeiling,
                        @JsonProperty("start_time") String startTime,
                        @JsonProperty("end_time") String endTime) {

        static RunVO of(WAgentRunDO run) {
            return new RunVO(run.getId(), run.getQuestionId(), run.getMissionId(), run.getStatus(),
                    run.getPlanSource(), run.getAutomationCeiling(),
                    run.getStartTime() == null ? null : run.getStartTime().toString(),
                    run.getEndTime() == null ? null : run.getEndTime().toString());
        }
    }

    /** 进度能力位（4 键；当前恒 polling）。 */
    public record ProgressCapabilityVO(@JsonProperty("channel") String channel,
                                       @JsonProperty("degrade_code") String degradeCode,
                                       @JsonProperty("poll_path") String pollPath,
                                       @JsonProperty("fallback") String fallback) {

        static ProgressCapabilityVO of(String runId) {
            return new ProgressCapabilityVO("polling", DEGRADE_CODE_POLLING,
                    WAgentApiPaths.resolve(WAgentApiPaths.RUN_BY_ID, WAgentApiPaths.P_RUN_ID, runId),
                    "polling");
        }
    }

    /** #5 建 Run 响应（Run 8 键 + idempotent_replay；异步通道再追加两键）。 */
    public record RunStartVO(@JsonProperty("run_id") String runId,
                             @JsonProperty("question_id") String questionId,
                             @JsonProperty("mission_id") String missionId,
                             @JsonProperty("status") String status,
                             @JsonProperty("plan_source") String planSource,
                             @JsonProperty("automation_ceiling") Integer automationCeiling,
                             @JsonProperty("start_time") String startTime,
                             @JsonProperty("end_time") String endTime,
                             @JsonProperty("idempotent_replay") boolean idempotentReplay,
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             @JsonProperty("execution_dispatch") String executionDispatch,
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             @JsonProperty("progress_channel") ProgressCapabilityVO progressChannel) {

        static RunStartVO of(RunVO v, boolean idempotentReplay) {
            return new RunStartVO(v.runId(), v.questionId(), v.missionId(), v.status(), v.planSource(),
                    v.automationCeiling(), v.startTime(), v.endTime(), idempotentReplay, null, null);
        }

        RunStartVO withAsyncDispatch(ProgressCapabilityVO capability) {
            return new RunStartVO(runId, questionId, missionId, status, planSource, automationCeiling,
                    startTime, endTime, idempotentReplay,
                    "pending#wagent-run-dispatch(#130)", capability);
        }
    }

    /** #6 状态 + 时间线（Run 8 键 + timeline）。 */
    public record RunDetailVO(@JsonProperty("run_id") String runId,
                              @JsonProperty("question_id") String questionId,
                              @JsonProperty("mission_id") String missionId,
                              @JsonProperty("status") String status,
                              @JsonProperty("plan_source") String planSource,
                              @JsonProperty("automation_ceiling") Integer automationCeiling,
                              @JsonProperty("start_time") String startTime,
                              @JsonProperty("end_time") String endTime,
                              @JsonProperty("timeline") RunTimelineVO timeline) {

        static RunDetailVO of(RunVO v, RunTimelineVO timeline) {
            return new RunDetailVO(v.runId(), v.questionId(), v.missionId(), v.status(), v.planSource(),
                    v.automationCeiling(), v.startTime(), v.endTime(), timeline);
        }
    }

    /** #7 SSE 降级载荷（能力位 4 键 + last_event_id_received）。 */
    public record SseFallbackVO(@JsonProperty("channel") String channel,
                                @JsonProperty("degrade_code") String degradeCode,
                                @JsonProperty("poll_path") String pollPath,
                                @JsonProperty("fallback") String fallback,
                                @JsonProperty("last_event_id_received") boolean lastEventIdReceived) {

        static SseFallbackVO of(ProgressCapabilityVO capability, boolean lastEventIdReceived) {
            return new SseFallbackVO(capability.channel(), capability.degradeCode(),
                    capability.pollPath(), capability.fallback(), lastEventIdReceived);
        }
    }

    @PostMapping(WAgentApiPaths.RUNS_COLLECTION)
    public ResponseEntity<ApiResponse<RunStartVO>> startRun(
            @RequestBody RunStartRequest req,
            @RequestHeader(value = HEADER_PREFER, required = false) String prefer,
            @RequestHeader(value = HEADER_IDEMPOTENCY_KEY, required = false) String idemHeader) {
        Identity identity = identityResolver.require();
        String idemKey = req.idempotencyKey() != null && !req.idempotencyKey().isBlank()
                ? req.idempotencyKey() : idemHeader;
        WAgentOrchestrator.RunStartOutcome outcome = orchestrator.startRun(
                new WAgentOrchestrator.RunStartCommand(identity.tenantId(), identity.orgId(),
                        identity.operator(), req.questionId(), null, idemKey,
                        req.automationCeiling(), req.parentRunId(), req.previousRunId(),
                        req.situationSnapshotJson()));

        RunStartVO data = RunStartVO.of(RunVO.of(outcome.run()), outcome.idempotentReplay());
        if (isAsyncPrefer(prefer)) {
            // 202 = 已受理建 Run；执行派发待 #130 调度接驳——如实标注，禁伪称后台已跑
            return ResponseEntity.status(202)
                    .body(ok(data.withAsyncDispatch(ProgressCapabilityVO.of(outcome.run().getId()))));
        }
        WAgentRunDO advanced = orchestrator.runPipeline(outcome.run().getId(), identity.operator());
        return ResponseEntity.ok(ok(RunStartVO.of(RunVO.of(advanced), outcome.idempotentReplay())));
    }

    /** #6 状态 + 时间线（run_event 正序 + step 摘要；只读经编排器，租户掩蔽）。 */
    @GetMapping(WAgentApiPaths.RUN_BY_ID)
    public ResponseEntity<ApiResponse<RunDetailVO>> getRun(
            @PathVariable(WAgentApiPaths.P_RUN_ID) String runId) {
        Identity identity = identityResolver.require();
        WAgentRunDO run = orchestrator.requireRunForTenant(runId, identity.tenantId());
        return ResponseEntity.ok(ok(RunDetailVO.of(RunVO.of(run), orchestrator.timeline(runId))));
    }

    /**
     * #7 SSE 进度流——P-4 未就绪：如实 502 + 轮询回落指引（禁伪流、禁 200 空挂）。
     * 就绪后本方法切 {@code text/event-stream} + Last-Event-ID 续传（F10-16，登记 #130/R4）。
     */
    @GetMapping(value = WAgentApiPaths.RUN_EVENTS_SSE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<SseFallbackVO>> streamRunEvents(
            @PathVariable(WAgentApiPaths.P_RUN_ID) String runId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        Identity identity = identityResolver.require();
        orchestrator.requireRunForTenant(runId, identity.tenantId()); // 存在性/租户校验先行（防未认证枚举探测通道差异）
        String traceId = AiTraceSupport.currentTraceId();
        ApiResponse<SseFallbackVO> body = ApiResponse.<SseFallbackVO>error(
                WAgentErrorCode.E_WA_DEP.httpStatus(), WAgentErrorCode.E_WA_DEP.wireCode(),
                WAgentErrorCode.E_WA_DEP.renderMessage(traceId, "SSE 进度通道(P-4/F10-16)")).withTraceId(traceId);
        body.setData(SseFallbackVO.of(ProgressCapabilityVO.of(runId), lastEventId != null));
        return ResponseEntity.status(WAgentErrorCode.E_WA_DEP.httpStatus()).body(body);
    }

    /** 进度能力位（SSE/轮询二态单源探测面；当前恒 polling）。 */
    @GetMapping(WAgentApiPaths.RUN_PROGRESS_CAPABILITY)
    public ApiResponse<ProgressCapabilityVO> progressCapability(
            @PathVariable(WAgentApiPaths.P_RUN_ID) String runId) {
        Identity identity = identityResolver.require();
        orchestrator.requireRunForTenant(runId, identity.tenantId());
        return ok(ProgressCapabilityVO.of(runId));
    }

    /** #8 awaiting_input 应答 ⇒ executing 续跑（同步通道内推进）。 */
    @PostMapping(WAgentApiPaths.RUN_INPUT)
    public ResponseEntity<ApiResponse<RunVO>> submitRunInput(
            @PathVariable(WAgentApiPaths.P_RUN_ID) String runId,
            @RequestBody(required = false) RunInputRequest req,
            @RequestHeader(value = HEADER_PREFER, required = false) String prefer) {
        Identity identity = identityResolver.require();
        orchestrator.requireRunForTenant(runId, identity.tenantId());
        WAgentRunDO run = orchestrator.submitInput(runId, req == null ? Map.of() : req.input(),
                identity.operator());
        if (!isAsyncPrefer(prefer)) {
            run = orchestrator.runPipeline(runId, identity.operator());
        }
        return ResponseEntity.ok(ok(RunVO.of(run)));
    }

    /** #9 审批闸门（凭证校验在编排面；清闸后同步通道续跑）。 */
    @PostMapping(WAgentApiPaths.RUN_APPROVAL)
    public ResponseEntity<ApiResponse<RunVO>> approveRunGate(
            @PathVariable(WAgentApiPaths.P_RUN_ID) String runId,
            @RequestBody RunApprovalRequest req,
            @RequestHeader(value = HEADER_PREFER, required = false) String prefer) {
        Identity identity = identityResolver.require();
        orchestrator.requireRunForTenant(runId, identity.tenantId());
        // 严格凭证校验 + 一次性消费（V326 数据面）：清闸是不可回退动作，消费必须与执行同批
        approvalTokenService.verifyAndConsume(req == null ? null : req.approvalToken(),
                WAgentApprovalScope.RUN_APPROVAL.name(), runId, identity.operator(),
                AiTraceSupport.currentTraceId());
        WAgentRunDO run = orchestrator.approveGate(runId,
                req == null ? null : req.approvalToken(), identity.operator());
        if (!isAsyncPrefer(prefer)) {
            run = orchestrator.runPipeline(runId, identity.operator());
        }
        return ResponseEntity.ok(ok(RunVO.of(run)));
    }

    /** #10 幂等取消（终态再取消 = 无操作成功，不报错）。 */
    @PostMapping(WAgentApiPaths.RUN_CANCEL)
    public ApiResponse<RunVO> cancelRun(@PathVariable(WAgentApiPaths.P_RUN_ID) String runId) {
        Identity identity = identityResolver.require();
        orchestrator.requireRunForTenant(runId, identity.tenantId());
        return ok(RunVO.of(orchestrator.cancel(runId, identity.operator())));
    }

    // ── 小件 ────────────────────────────────────────────────────────

    static boolean isAsyncPrefer(String prefer) {
        return prefer != null && prefer.toLowerCase(Locale.ROOT).contains(PREFER_RESPOND_ASYNC);
    }

    /** 强类型载荷版（#147CN）：与 Map 版同语义（仅多带 traceId），供各端点复用。 */
    static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.success(data).withTraceId(AiTraceSupport.currentTraceId());
    }
}
