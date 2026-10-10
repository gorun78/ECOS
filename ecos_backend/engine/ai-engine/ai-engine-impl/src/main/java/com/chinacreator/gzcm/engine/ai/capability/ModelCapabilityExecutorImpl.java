package com.chinacreator.gzcm.engine.ai.capability;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.ai.capability.AiCapabilityId;
import com.chinacreator.gzcm.engine.ai.capability.CapabilityResult.Quality;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.runtime.llm.config.LLMGatewayProperties;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.EmbeddingRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.EmbeddingResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Model &amp; Capability Center 统一执行实现（《补充设计 v1.0》§10，落地 {@link ModelCapabilityExecutor}）。
 *
 * <p><b>红线落位</b>：</p>
 * <ul>
 *   <li><b>LLM/Embedding 唯一出口</b>仍为 {@code runtime/llm-gateway}（铁律 §2.5），本类只调
 *       {@link LLMGateway#call}/{@code embed}，<b>不</b>new 任何 Provider；OCR/ASR 等其余能力
 *       一期未接模型 → 一律回 <b>FAILED + 明确告警</b>（§10.6 降级不满足门槛禁伪装 SUCCEEDED），
 *       绝不本地伪造成功结果（§16 SUP-MODEL-03）；</li>
 *   <li><b>异步一律经 runtime-task</b>（{@link ITaskManagementService#submitTask}，SUP-GOV-02），
 *       引擎内禁自建调度器/裸线程/Executors（ArchUnit 守）；</li>
 *   <li><b>台账</b>落 V269 {@code ecos_ai_capability_task}，每次调用必带 traceId + modelRef/modelVersion
 *       （§12.2 / SUP-MODEL-02）；未校准置信 {@code quality.kind=MODEL_ESTIMATE} 时 calibrated 恒不落 1
 *       （SUP-MODEL-03）；</li>
 *   <li><b>事件</b>成功/失败各至多一条 {@link EventTypes.Cognition#CAPABILITY_INVOKED} →
 *       {@link KafkaTopics#AGENT}（禁新造 topic；EventBus 不可用仅 WARN 不阻断）。</li>
 * </ul>
 *
 * <p>协作者全部 <b>ObjectProvider 构造注入</b>（可空降级）：llm-gateway / runtime-task /
 * runtime-event 任一缺失时对应能力显式失败或降级，不静默。单测（非 IT）直接以 mock 构造。</p>
 */
@Service
public class ModelCapabilityExecutorImpl implements ModelCapabilityExecutor {

    private static final Logger log = LoggerFactory.getLogger(ModelCapabilityExecutorImpl.class);

    /** runtime-task 任务类型词（registerExecutor 同字面量）。 */
    public static final String ASYNC_TASK_TYPE = "AI_CAPABILITY";
    private static final String TASK_KIND_SYNC = "SYNC";
    private static final String TASK_KIND_ASYNC = "ASYNC";
    private static final String DOMAIN_DEFAULT = "default";
    private static final String VERSION_NO_1 = "1";
    private static final String TENANT_DEFAULT = "default";

    private final CapabilityLedgerDao ledgerDao;
    private final ObjectProvider<LLMGateway> llmGatewayProvider;
    private final ObjectProvider<LLMGatewayProperties> llmPropsProvider;
    private final ObjectProvider<ITaskManagementService> taskManagementProvider;
    private final ObjectProvider<EventBusService> eventBusProvider;

    public ModelCapabilityExecutorImpl(CapabilityLedgerDao ledgerDao,
                                       ObjectProvider<LLMGateway> llmGatewayProvider,
                                       ObjectProvider<LLMGatewayProperties> llmPropsProvider,
                                       ObjectProvider<ITaskManagementService> taskManagementProvider,
                                       ObjectProvider<EventBusService> eventBusProvider) {
        this.ledgerDao = ledgerDao;
        this.llmGatewayProvider = llmGatewayProvider;
        this.llmPropsProvider = llmPropsProvider;
        this.taskManagementProvider = taskManagementProvider;
        this.eventBusProvider = eventBusProvider;
    }

    // ── 同步执行 ────────────────────────────────────────────────────

    @Override
    public CapabilityResult execute(CapabilityRequest request) {
        CapabilityResult r = validate(request);
        long start = System.currentTimeMillis();
        AiCapabilityId cap = request.getCapabilityId();
        String traceId = resolveTraceId(request);
        r.setTraceId(traceId);

        LLMGateway llm = llmGatewayProvider.getIfAvailable();
        try {
            switch (cap) {
                case LLM_TEXT_REASONING -> runLlmText(request, llm, r);
                case TEXT_EMBEDDING -> runEmbedding(request, llm, r);
                default -> notWired(cap, r);
            }
        } catch (Exception e) {
            // 异常不静默：标 FAILED + 告警，依赖失败不包成"成功但为空"（§12.2）
            r.setStatus(CapabilityState.FAILED);
            r.setDegraded(false);
            addWarning(r, "执行异常: " + e.getClass().getSimpleName());
            log.warn("[capability] 同步执行异常 cap={} traceId={}", cap, traceId, e);
        }

        r.setLatencyMs(System.currentTimeMillis() - start);
        persistSync(request, r, UUID.randomUUID().toString(), traceId);
        publishInvoked(request, r);
        return r;
    }

    private void runLlmText(CapabilityRequest request, LLMGateway llm, CapabilityResult r) {
        if (llm == null) {
            notWired(AiCapabilityId.LLM_TEXT_REASONING, r);
            return;
        }
        String model = request.getModelRef() != null && !request.getModelRef().isBlank()
                ? request.getModelRef() : defaultModel();
        ChatRequest cr = new ChatRequest(model,
                List.of(new ChatMessage("user", extractPrompt(request))),
                0.0, null, false);
        cr.setCallerId("ai-engine:capability:LLM_TEXT_REASONING");
        cr.setSessionId(request.getRunId());
        ChatResponse resp = llm.call(cr);
        if (resp == null || !resp.isSuccess()) {
            markFailed(r, "LLM 调用失败", resp == null ? null : resp.getErrorCode());
            return;
        }
        r.setStatus(CapabilityState.SUCCEEDED);
        r.setModelRef(model);
        r.setModelVersion(request.getModelVersion());
        r.setOutputRef("cap-llm-" + r.getTraceId());
        r.setCostUnit(AiCapabilityId.LLM_TEXT_REASONING.costUnit());
        r.setCostValue((long) (resp.getTokensInput() + resp.getTokensOutput()));
        // 模型自报置信 → MODEL_ESTIMATE，calibrated 恒 false（SUP-MODEL-03）
        r.setQuality(new Quality(null, Quality.KIND_MODEL_ESTIMATE, Boolean.FALSE));
        r.setSelectedModelReason("requested=LLM_TEXT_REASONING; selected=" + model + "; reason=catalog-default");
    }

    private void runEmbedding(CapabilityRequest request, LLMGateway llm, CapabilityResult r) {
        if (llm == null) {
            notWired(AiCapabilityId.TEXT_EMBEDDING, r);
            return;
        }
        String model = request.getModelRef() != null && !request.getModelRef().isBlank()
                ? request.getModelRef() : embeddingModel();
        EmbeddingRequest er = new EmbeddingRequest(extractPrompt(request), model);
        EmbeddingResponse resp = llm.embed(er);
        if (resp == null || !resp.isSuccess()) {
            markFailed(r, "Embedding 调用失败", resp == null ? null : resp.getErrorMsg());
            return;
        }
        r.setStatus(CapabilityState.SUCCEEDED);
        r.setModelRef(model);
        r.setModelVersion(request.getModelVersion());
        r.setOutputRef("cap-emb-" + r.getTraceId());
        r.setCostUnit(AiCapabilityId.TEXT_EMBEDDING.costUnit());
        r.setCostValue(resp.getTokensTotal() > 0 ? resp.getTokensTotal() : 1L);
        r.setQuality(new Quality(null, Quality.KIND_MODEL_ESTIMATE, Boolean.FALSE));
        r.setSelectedModelReason("requested=TEXT_EMBEDDING; selected=" + model + "; reason=catalog-default");
    }

    /** 一期未接模型的能力：真实回报 FAILED + 告警，禁伪造成功（§10.6）。 */
    private void notWired(AiCapabilityId cap, CapabilityResult r) {
        r.setStatus(CapabilityState.FAILED);
        r.setDegraded(false);
        r.setOutputRef(null);
        r.setCostUnit(cap.costUnit());
        addWarning(r, "能力 " + cap.name() + " 一期未接入模型 Provider，禁伪造成功（§10.6 未达门槛必须 FAILED）");
    }

    private void markFailed(CapabilityResult r, String kind, String codeOrMsg) {
        r.setStatus(CapabilityState.FAILED);
        r.setDegraded(false);
        log.warn("[capability] {} 失败 codeOrMsg={}（原文只进日志，禁进响应体 X-49）", kind, codeOrMsg);
        addWarning(r, kind + "（诊断见服务端日志）");
    }

    // ── 异步提交（runtime-task 单通道）─────────────────────────────

    @Override
    public AsyncCapabilityHandle submit(CapabilityRequest request) {
        validate(request);
        AiCapabilityId cap = request.getCapabilityId();
        String traceId = resolveTraceId(request);
        ITaskManagementService task = taskManagementProvider.getIfAvailable();
        if (task == null) {
            log.warn("[capability] submit 失败：runtime-task 未装配 cap={} traceId={}", cap, traceId);
            throw new IllegalStateException("runtime-task 未装配，无法异步提交能力任务（SUP-GOV-02）");
        }
        TaskDescription td = new TaskDescription();
        td.setTaskName("capability-" + cap.name());
        td.setTaskType(ASYNC_TASK_TYPE);
        td.setAsync(Boolean.TRUE);
        td.setParameters(request.asParams());
        td.setTenantId(request.getTenantId());
        td.setCreatedBy(request.getSubmitter());
        String taskId;
        try {
            taskId = task.submitTask(td);   // runtime-task 生成/回显 taskId（引擎不自建调度）
        } catch (ITaskManagementService.TaskManagementException e) {
            log.warn("[capability] submit 落 runtime-task 失败 cap={} traceId={}", cap, traceId, e);
            throw new IllegalStateException("异步能力任务提交 runtime-task 失败: " + e.getMessage(), e);
        }
        CapabilityTaskDO ledger = baseLedger(cap, request, traceId, taskId);
        ledger.setTaskKind(TASK_KIND_ASYNC);
        ledger.setStatus(CapabilityState.PENDING.name());
        ledgerDao.insertTask(ledger);
        log.info("[capability] 异步提交 cap={} taskId={} traceId={}", cap, taskId, traceId);
        return new AsyncCapabilityHandle(taskId, traceId, CapabilityState.SUBMITTED);
    }

    // ── 状态回显（台账只读投影）────────────────────────────────────

    @Override
    public CapabilityStatus getStatus(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;   // 调用方映射 404，禁伪造空成功
        }
        return ledgerDao.findById(taskId)
                .map(ModelCapabilityExecutorImpl::toStatus)
                .orElse(null);
    }

    private static CapabilityStatus toStatus(CapabilityTaskDO t) {
        CapabilityStatus s = new CapabilityStatus();
        s.setTaskId(t.getId());
        s.setCapabilityId(t.getCapabilityId());
        s.setStatus(t.getStatus());
        s.setInputRef(t.getInputRef());
        s.setOutputRef(t.getOutputRef());
        s.setModelRef(t.getModelRef());
        s.setModelVersion(t.getModelVersion());
        s.setQualityScore(t.getQualityScore());
        s.setQualityKind(t.getQualityKind());
        s.setCalibrated(t.getCalibrated() == null ? null : t.getCalibrated() == 1);
        s.setLatencyMs(t.getLatencyMs());
        s.setCostUnit(t.getCostUnit());
        s.setDegraded(t.getDegraded() == null ? null : t.getDegraded() == 1);
        s.setWarningsText(t.getWarningsText());
        s.setTraceId(t.getTraceId());
        s.setRunId(t.getRunId());
        return s;
    }

    // ── 内部辅助 ──────────────────────────────────────────────────

    private CapabilityResult validate(CapabilityRequest request) {
        if (request == null || request.getCapabilityId() == null) {
            throw new IllegalArgumentException("能力请求缺 capabilityId（§10.4，词表单源 AiCapabilityId）");
        }
        if (request.getInputRef() == null || request.getInputRef().isBlank()) {
            throw new IllegalArgumentException("能力请求缺 inputRef（§10.5：输入只放引用 ID，NOT NULL）");
        }
        CapabilityResult r = new CapabilityResult();
        r.setCapabilityId(request.getCapabilityId().name());
        r.setInputRef(request.getInputRef());
        return r;
    }

    private String resolveTraceId(CapabilityRequest request) {
        String t = request.getTraceId();
        return (t != null && !t.isBlank()) ? t : AiTraceSupport.currentTraceId();
    }

    private String defaultModel() {
        LLMGatewayProperties p = llmPropsProvider.getIfAvailable();
        return p != null ? p.getEngine().getDefaultModel() : null;
    }

    private String embeddingModel() {
        LLMGatewayProperties p = llmPropsProvider.getIfAvailable();
        return p != null ? p.getEngine().getEmbeddingModel() : null;
    }

    /** inputRef 二期承载「引用→正文」落 runtime-access/MinIO；一期直执仅取引用键做 prompt。 */
    private String extractPrompt(CapabilityRequest request) {
        Object p = request.getParams() == null ? null : request.getParams().get("prompt");
        if (p instanceof String s && !s.isBlank()) {
            return s;
        }
        return request.getInputRef();
    }

    private void addWarning(CapabilityResult r, String w) {
        if (r.getWarnings() == null) {
            r.setWarnings(new ArrayList<>());
        }
        r.getWarnings().add(w);
    }

    private CapabilityTaskDO baseLedger(AiCapabilityId cap, CapabilityRequest request,
                                        String traceId, String taskId) {
        CapabilityTaskDO t = new CapabilityTaskDO();
        t.setId(taskId);
        t.setCapabilityId(cap.name());
        t.setInputRef(request.getInputRef());
        t.setToolContractRef(request.getToolContractRef());
        t.setRunId(request.getRunId());
        t.setSubmitter(request.getSubmitter());
        t.setTraceId(traceId);
        t.setCostUnit(cap.costUnit());
        t.setTenantId(request.getTenantId() == null ? TENANT_DEFAULT : request.getTenantId());
        t.setOrgId(request.getOrgId());
        t.setDomain(DOMAIN_DEFAULT);
        t.setVersionNo(VERSION_NO_1);
        t.setCreateBy(request.getSubmitter());
        t.setUpdateBy(request.getSubmitter());
        return t;
    }

    private void persistSync(CapabilityRequest request, CapabilityResult r,
                             String ledgerId, String traceId) {
        CapabilityState state = r.getStatus() == null ? CapabilityState.FAILED : r.getStatus();
        CapabilityTaskDO t = baseLedger(request.getCapabilityId(), request, traceId, ledgerId);
        t.setTaskKind(TASK_KIND_SYNC);
        t.setStatus(state.name());
        t.setModelRef(r.getModelRef());
        t.setModelVersion(r.getModelVersion());
        t.setOutputRef(r.getOutputRef());
        t.setLatencyMs(r.getLatencyMs());
        t.setCostUnit(r.getCostUnit() == null ? request.getCapabilityId().costUnit() : r.getCostUnit());
        t.setDegraded(r.isDegraded() || state == CapabilityState.DEGRADED ? 1 : 0);
        if (r.getQuality() != null) {
            t.setQualityScore(r.getQuality().score());
            t.setQualityKind(r.getQuality().kind());
            // 未校准 → calibrated 落 0（禁留 1；SUP-MODEL-03）
            t.setCalibrated(Boolean.TRUE.equals(r.getQuality().calibrated()) ? 1 : 0);
        } else {
            t.setQualityKind(Quality.KIND_MODEL_ESTIMATE);
            t.setCalibrated(0);
        }
        t.setWarningsText(toWarningsJson(r.getWarnings()));
        t.setSelectedModelReason(r.getSelectedModelReason());
        try {
            ledgerDao.insertTask(t);
        } catch (Exception e) {
            // 台账写失败：发 WARN，禁把整体调用吞成"没发生"（§12.2 真实错误传播）
            log.warn("[capability] 台账写失败 cap={} ledgerId={} traceId={}",
                    request.getCapabilityId(), ledgerId, traceId, e);
        }
    }

    private String toWarningsJson(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(warnings);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void publishInvoked(CapabilityRequest request, CapabilityResult r) {
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", EventTypes.Cognition.CAPABILITY_INVOKED);
            payload.put("capabilityId", request.getCapabilityId().name());
            payload.put("traceId", r.getTraceId());
            payload.put("modelRef", r.getModelRef());
            payload.put("modelVersion", r.getModelVersion());
            payload.put("latencyMs", r.getLatencyMs());
            payload.put("status", r.getStatus() == null ? null : r.getStatus().name());
            bus.publish(KafkaTopics.AGENT, payload);
        } catch (Exception e) {
            log.warn("[capability] CAPABILITY_INVOKED 事件发布失败（不阻断主流程）cap={}",
                    request.getCapabilityId(), e);
        }
    }
}
