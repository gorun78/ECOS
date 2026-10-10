package com.chinacreator.gzcm.engine.ai.wagent.web;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrator;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
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

    public WAgentRunController(WAgentOrchestrator orchestrator, WAgentIdentityResolver identityResolver) {
        this.orchestrator = orchestrator;
        this.identityResolver = identityResolver;
    }

    /** #5 建 Run 请求体（automationCeiling 0~3，缺省 L1 fail-closed 默认）。 */
    public record RunStartRequest(String questionId, String idempotencyKey, Integer automationCeiling,
                                  String parentRunId, String previousRunId, String situationSnapshotJson) {
    }

    /** #8 awaiting_input 应答体（载荷只作快照引用，禁正文入事件）。 */
    public record RunInputRequest(Map<String, Object> input) {
    }

    /** #9 审批体（approval_token 必填，凭证校验在编排面）。 */
    public record RunApprovalRequest(String approvalToken) {
    }

    @PostMapping(WAgentApiPaths.RUNS_COLLECTION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> startRun(
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

        Map<String, Object> data = runView(outcome.run());
        data.put("idempotent_replay", outcome.idempotentReplay());

        if (isAsyncPrefer(prefer)) {
            // 202 = 已受理建 Run；执行派发待 #130 调度接驳——如实标注，禁伪称后台已跑
            data.put("execution_dispatch", "pending#wagent-run-dispatch(#130)");
            data.put("progress_channel", progressCapabilityBody(outcome.run().getId()));
            return ResponseEntity.status(202).body(ok(data));
        }
        WAgentRunDO advanced = orchestrator.runPipeline(outcome.run().getId(), identity.operator());
        data = runView(advanced);
        data.put("idempotent_replay", outcome.idempotentReplay());
        return ResponseEntity.ok(ok(data));
    }

    /** #6 状态 + 时间线（run_event 正序 + step 摘要；只读经编排器，租户掩蔽）。 */
    @GetMapping(WAgentApiPaths.RUN_BY_ID)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRun(
            @PathVariable(WAgentApiPaths.P_RUN_ID) String runId) {
        Identity identity = identityResolver.require();
        WAgentRunDO run = orchestrator.requireRunForTenant(runId, identity.tenantId());
        Map<String, Object> data = runView(run);
        data.put("timeline", orchestrator.timeline(runId));
        return ResponseEntity.ok(ok(data));
    }

    /**
     * #7 SSE 进度流——P-4 未就绪：如实 502 + 轮询回落指引（禁伪流、禁 200 空挂）。
     * 就绪后本方法切 {@code text/event-stream} + Last-Event-ID 续传（F10-16，登记 #130/R4）。
     */
    @GetMapping(value = WAgentApiPaths.RUN_EVENTS_SSE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> streamRunEvents(
            @PathVariable(WAgentApiPaths.P_RUN_ID) String runId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        Identity identity = identityResolver.require();
        orchestrator.requireRunForTenant(runId, identity.tenantId()); // 存在性/租户校验先行（防未认证枚举探测通道差异）
        String traceId = AiTraceSupport.currentTraceId();
        Map<String, Object> data = progressCapabilityBody(runId);
        data.put("last_event_id_received", lastEventId != null);
        ApiResponse<Map<String, Object>> body = ApiResponse.<Map<String, Object>>error(
                WAgentErrorCode.E_WA_DEP.httpStatus(), WAgentErrorCode.E_WA_DEP.wireCode(),
                WAgentErrorCode.E_WA_DEP.renderMessage(traceId, "SSE 进度通道(P-4/F10-16)")).withTraceId(traceId);
        body.setData(data);
        return ResponseEntity.status(WAgentErrorCode.E_WA_DEP.httpStatus()).body(body);
    }

    /** 进度能力位（SSE/轮询二态单源探测面；当前恒 polling）。 */
    @GetMapping(WAgentApiPaths.RUN_PROGRESS_CAPABILITY)
    public ApiResponse<Map<String, Object>> progressCapability(
            @PathVariable(WAgentApiPaths.P_RUN_ID) String runId) {
        Identity identity = identityResolver.require();
        orchestrator.requireRunForTenant(runId, identity.tenantId());
        return ok(progressCapabilityBody(runId));
    }

    /** #8 awaiting_input 应答 ⇒ executing 续跑（同步通道内推进）。 */
    @PostMapping(WAgentApiPaths.RUN_INPUT)
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitRunInput(
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
        return ResponseEntity.ok(ok(runView(run)));
    }

    /** #9 审批闸门（凭证非空校验在编排面；清闸后同步通道续跑）。 */
    @PostMapping(WAgentApiPaths.RUN_APPROVAL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveRunGate(
            @PathVariable(WAgentApiPaths.P_RUN_ID) String runId,
            @RequestBody RunApprovalRequest req,
            @RequestHeader(value = HEADER_PREFER, required = false) String prefer) {
        Identity identity = identityResolver.require();
        orchestrator.requireRunForTenant(runId, identity.tenantId());
        WAgentRunDO run = orchestrator.approveGate(runId,
                req == null ? null : req.approvalToken(), identity.operator());
        if (!isAsyncPrefer(prefer)) {
            run = orchestrator.runPipeline(runId, identity.operator());
        }
        return ResponseEntity.ok(ok(runView(run)));
    }

    /** #10 幂等取消（终态再取消 = 无操作成功，不报错）。 */
    @PostMapping(WAgentApiPaths.RUN_CANCEL)
    public ApiResponse<Map<String, Object>> cancelRun(@PathVariable(WAgentApiPaths.P_RUN_ID) String runId) {
        Identity identity = identityResolver.require();
        orchestrator.requireRunForTenant(runId, identity.tenantId());
        return ok(runView(orchestrator.cancel(runId, identity.operator())));
    }

    // ── 小件 ────────────────────────────────────────────────────────

    static boolean isAsyncPrefer(String prefer) {
        return prefer != null && prefer.toLowerCase(Locale.ROOT).contains(PREFER_RESPOND_ASYNC);
    }

    private static Map<String, Object> progressCapabilityBody(String runId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("channel", "polling");
        m.put("degrade_code", DEGRADE_CODE_POLLING);
        m.put("poll_path", WAgentApiPaths.resolve(WAgentApiPaths.RUN_BY_ID,
                WAgentApiPaths.P_RUN_ID, runId));
        m.put("fallback", "polling");
        return m;
    }

    static Map<String, Object> runView(WAgentRunDO run) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("run_id", run.getId());
        m.put("question_id", run.getQuestionId());
        m.put("mission_id", run.getMissionId());
        m.put("status", run.getStatus());
        m.put("plan_source", run.getPlanSource());
        m.put("automation_ceiling", run.getAutomationCeiling());
        m.put("start_time", run.getStartTime() == null ? null : run.getStartTime().toString());
        m.put("end_time", run.getEndTime() == null ? null : run.getEndTime().toString());
        return m;
    }

    static ApiResponse<Map<String, Object>> ok(Map<String, Object> data) {
        return ApiResponse.success(data).withTraceId(AiTraceSupport.currentTraceId());
    }
}
