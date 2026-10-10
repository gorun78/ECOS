package com.chinacreator.gzcm.engine.ai.capability.web;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.capability.AiCapabilityId;
import com.chinacreator.gzcm.engine.ai.capability.AsyncCapabilityHandle;
import com.chinacreator.gzcm.engine.ai.capability.CapabilityReadService;
import com.chinacreator.gzcm.engine.ai.capability.CapabilityReadVO;
import com.chinacreator.gzcm.engine.ai.capability.CapabilityRequest;
import com.chinacreator.gzcm.engine.ai.capability.CapabilityResult;
import com.chinacreator.gzcm.engine.ai.capability.CapabilityStatus;
import com.chinacreator.gzcm.engine.ai.capability.ModelCapabilityExecutor;
import com.chinacreator.gzcm.engine.ai.capability.web.AiCapabilityApiPaths;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Model &amp; Capability Center 读/执行端 REST（《补充设计 v1.0》§13 /api/v1/ai/capabilities 族）。
 *
 * <p>端点（operationId 与 OAS 对齐，SUP-GOV-03）：</p>
 * <ul>
 *   <li>{@code GET  /api/v1/ai/capabilities} → getCapabilities（目录 + 契约版本，只读）；</li>
 *   <li>{@code POST /api/v1/ai/capabilities/{id}/execute} → executeCapability（同步，统一路由）；</li>
 *   <li>{@code POST /api/v1/ai/capabilities/{id}/tasks} → submitCapabilityTask（异步，runtime-task，202 + taskId）；</li>
 *   <li>{@code GET  /api/v1/ai/capabilities/tasks/{taskId}/status} → getCapabilityTaskStatus（台账回显）。</li>
 * </ul>
 *
 * <p>路径常量单源 {@link AiCapabilityApiPaths}，禁散落字面量；{@code {id}} 走
 * {@link AiCapabilityId} 词表，未知能力 404（§13.1：禁造空成功）。</p>
 */
@RestController
@RequestMapping(AiCapabilityApiPaths.BASE_PREFIX)
public class AiCapabilityController {

    private static final Logger log = LoggerFactory.getLogger(AiCapabilityController.class);
    private static final String PATH_CAPABILITIES = "/capabilities";

    private final CapabilityReadService readService;
    private final ModelCapabilityExecutor executor;

    public AiCapabilityController(CapabilityReadService readService, ModelCapabilityExecutor executor) {
        this.readService = readService;
        this.executor = executor;
    }

    /** §13 #1 getCapabilities：查询可用能力与契约版本（只读）。 */
    @GetMapping(PATH_CAPABILITIES)
    public ApiResponse<List<CapabilityReadVO>> getCapabilities() {
        return ApiResponse.success(readService.listCapabilities())
                .withTraceId(AiTraceSupport.currentTraceId());
    }

    /** §13 #2 executeCapability：执行指定能力（同步，经统一路由；按 Tool Contract 决定副作用）。 */
    @PostMapping(AiCapabilityApiPaths.CAPABILITY_EXECUTE)
    public ResponseEntity<ApiResponse<CapabilityResult>> executeCapability(
            @PathVariable(AiCapabilityApiPaths.P_ID) String id,
            @RequestBody(required = false) CapabilityRequest body) {
        CapabilityRequest req = request(id, body);
        if (req == null) {
            return notFound404();
        }
        CapabilityResult result = executor.execute(req);
        return ResponseEntity.ok(ApiResponse.success(result).withTraceId(result.getTraceId()));
    }

    /** §13 #3 submitCapabilityTask：提交长耗时模型任务（异步，runtime-task 管理，返回 taskId）。 */
    @PostMapping(AiCapabilityApiPaths.CAPABILITY_TASKS)
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitCapabilityTask(
            @PathVariable(AiCapabilityApiPaths.P_ID) String id,
            @RequestBody(required = false) CapabilityRequest body) {
        CapabilityRequest req = request(id, body);
        if (req == null) {
            return notFound404();
        }
        AsyncCapabilityHandle handle = executor.submit(req);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", handle.getTaskId());
        data.put("traceId", handle.getTraceId());
        data.put("initialStatus", handle.getInitialStatus().name());
        return ResponseEntity.accepted().body(ApiResponse.success(data).withTraceId(handle.getTraceId()));
    }

    /** 状态回显：台账（V269）只读投影。未知 taskId ⇒ 404（§13.1 禁造空成功）。 */
    @GetMapping(AiCapabilityApiPaths.CAPABILITY_TASK_STATUS_BY_ID)
    public ResponseEntity<ApiResponse<CapabilityStatus>> getCapabilityTaskStatus(
            @PathVariable(AiCapabilityApiPaths.P_TASK_ID) String taskId) {
        CapabilityStatus s = executor.getStatus(taskId);
        if (s == null) {
            return ResponseEntity.status(404).body(ApiResponse.<CapabilityStatus>notFound("能力任务不存在: " + taskId)
                    .withTraceId(AiTraceSupport.currentTraceId()));
        }
        return ResponseEntity.ok(ApiResponse.success(s).withTraceId(AiTraceSupport.currentTraceId()));
    }

    // ── 请求装配 ──────────────────────────────────────────────────

    /** 以路径参数 {@code {id}} 定位能力（词表校验）；未知 ⇒ null（由调用方映射 404）。 */
    private CapabilityRequest request(String id, CapabilityRequest body) {
        AiCapabilityId cap = parse(id);
        if (cap == null) {
            log.warn("[capability] 未知能力 ID:{}（词表单源 AiCapabilityId）", id);
            return null;
        }
        CapabilityRequest req = body == null ? new CapabilityRequest() : body;
        req.setCapabilityId(cap);
        return req;
    }

    private AiCapabilityId parse(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return AiCapabilityId.valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> notFound404() {
        Map<String, Object> empty = new LinkedHashMap<>();
        return ResponseEntity.status(404)
                .body(ApiResponse.<T>notFound("未知能力 ID").withTraceId(AiTraceSupport.currentTraceId()));
    }
}
