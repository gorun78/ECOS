package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.service.profile.KbProfileDO;
import com.chinacreator.gzcm.engine.kb.service.profile.KbProfileService;
import com.chinacreator.gzcm.engine.kb.service.profile.KbProfileStore;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService.TaskManagementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F04-06 历史画像 REST（REQ-KB-02，D.3）。
 *
 * <ul>
 *   <li>POST /generate 202 → 经 {@link ITaskManagementService} 异步（禁本地线程，铁律 §2.5）；
 *       runtime-task 不可用 503 ECOS-KB-021</li>
 *   <li>GET  /            → 列表（limit ≤ 500，D.2-4）</li>
 *   <li>POST /{id}/publish → 403 ECOS-KB-062（非知识管理员）；409 状态机 / I-6（由 {@link KbErrorCodes.DomainException} 透传）</li>
 *   <li>GET  /resolve     → 命中画像 + 完整退化链</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/knowledge/profiles")
public class KbProfileController {

    private static final Logger log = LoggerFactory.getLogger(KbProfileController.class);
    private static final String TASK_TYPE = "KB_PROFILE_GENERATE";
    private static final String ROLE_HEADER = "X-Ecos-Role";
    private static final String ROLE_KB_ADMIN = "knowledge_admin";

    private final KbProfileService service;
    private final KbProfileStore store;
    private final ITaskManagementService taskManagement;

    public KbProfileController(KbProfileService service,
                               KbProfileStore store,
                               ITaskManagementService taskManagement) {
        this.service = service;
        this.store = store;
        this.taskManagement = taskManagement;
    }

    /** POST /generate：body = {@code ProfileGenerateRequest}。202 提交；503 底座不可用（C.3）。 */
    @PostMapping("/generate")
    @RequirePermission(value = "提交画像生成任务", permission = "knowledge:profile:generate")
    public ApiResponse<Map<String, Object>> generate(@RequestBody ProfileGenerateRequest req) {
        if (req == null || req.getMetricCodes() == null || req.getMetricCodes().isEmpty()) {
            return ApiResponse.badRequest("metricCodes 不能为空");
        }
        try {
            TaskDescription d = new TaskDescription();
            d.setTaskId("kbpg-" + UUID.randomUUID());
            d.setTaskName("kb_profile_generate:" + req.getMetricCodes().get(0));
            d.setTaskType(TASK_TYPE);
            d.setDescription("window=" + req.getWindowFrom() + "~" + req.getWindowTo());
            Map<String, Object> params = new HashMap<>();
            params.put("metricCodes", req.getMetricCodes());
            params.put("dims", req.getDims() == null ? Map.of() : req.getDims());
            params.put("windowFrom", req.getWindowFrom());
            params.put("windowTo", req.getWindowTo());
            params.put("dryRun", req.isDryRun());
            d.setParameters(params);
            d.setAsync(true);
            d.setCreatedBy("kb-profile-controller");
            String taskId = taskManagement.submitTask(d);
            Map<String, Object> out = new HashMap<>();
            out.put("taskId", taskId);
            out.put("status", "SUBMITTED");
            out.put("traceId", UUID.randomUUID().toString());
            return ApiResponse.<Map<String, Object>>success("已提交", out).withTraceId((String) out.get("traceId"));
        } catch (TaskManagementException | RuntimeException e) {
            log.error("画像生成提交失败: {}", e.getMessage(), e);
            return ApiResponse.<Map<String, Object>>error(503, KbErrorCodes.TASK_BASE_UNAVAILABLE.code(),
                    "runtime-task 不可用，禁降级为本地同步计算: " + e.getMessage());
        }
    }

    /** GET / → 列表（limit ≤ 500，D.2-4）。 */
    @GetMapping
    public ApiResponse<List<KbProfileDO>> list(
            @RequestParam(value = "metric", required = false) String metric,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        int cap = Math.max(1, Math.min(limit, 500));
        List<KbProfileDO> rows = store.query(metric, status, domain, null);
        return ApiResponse.success(rows.size() > cap ? rows.subList(0, cap) : rows);
    }

    /** POST /{id}/publish → 403 ECOS-KB-062（非管理员；D.3） | 409 ECOS-KB-061/060（由 DomainException 透传）。 */
    @PostMapping("/{id}/publish")
    @RequirePermission(value = "发布画像", permission = "knowledge:profile:publish")
    public ApiResponse<KbProfileDO> publish(@PathVariable String id,
                                            @RequestHeader(value = ROLE_HEADER, required = false) String role,
                                            @RequestHeader(value = "X-Ecos-User", required = false)
                                            String user) {
        String actor = (user == null || user.isBlank()) ? role : user;
        if (role == null || !role.contains(ROLE_KB_ADMIN)) {
            return ApiResponse.<KbProfileDO>error(403, KbErrorCodes.PERMISSION_DENIED.code(),
                    "权限不足：需 knowledge_admin 角色");
        }
        try {
            KbProfileDO row = service.publish(id, actor);
            return ApiResponse.<KbProfileDO>success(row).withTraceIdFromMdc();
        } catch (IllegalStateException e) {
            return mapMessage(e);
        }
    }

    /** GET /resolve → 命中画像 + 完整退化链（细→粗，D.3）。 */
    @GetMapping("/resolve")
    public ApiResponse<Map<String, Object>> resolve(
            @RequestParam(value = "metric") String metric,
            @RequestParam(value = "status", defaultValue = "PUBLISHED") String status,
            @RequestParam(value = "atVersion", required = false) String atVersion) {
        KbProfileService.ResolveResult r = service.resolve(metric, status, atVersion);
        if (r.profile() == null) {
            return ApiResponse.<Map<String, Object>>notFound("no profile matched " + metric + "/" + status);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("profile", r.profile());
        data.put("degradeChain", r.degradeChain());
        return ApiResponse.<Map<String, Object>>success(data).withTraceIdFromMdc();
    }

    // ── 领域异常 → ApiResponse 映射 ──────────────────────────────────────

    private ApiResponse<KbProfileDO> mapMessage(IllegalStateException e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("ECOS-KB-061")) {
            return ApiResponse.<KbProfileDO>error(409, "ECOS-KB-061", m);
        }
        if (m.contains("ECOS-KB-060")) {
            return ApiResponse.<KbProfileDO>error(409, "ECOS-KB-060", m);
        }
        return ApiResponse.<KbProfileDO>notFound(m);
    }

    /** D.3 生成请求体（metrics×dims×window，可 dryRun 预览样本量）。 */
    public static class ProfileGenerateRequest {
        private List<String> metricCodes;
        private Map<String, String> dims;
        private String windowFrom;
        private String windowTo;
        private boolean dryRun;

        public List<String> getMetricCodes() { return metricCodes; }
        public void setMetricCodes(List<String> metricCodes) { this.metricCodes = metricCodes; }
        public Map<String, String> getDims() { return dims; }
        public void setDims(Map<String, String> dims) { this.dims = dims; }
        public String getWindowFrom() { return windowFrom; }
        public void setWindowFrom(String windowFrom) { this.windowFrom = windowFrom; }
        public String getWindowTo() { return windowTo; }
        public void setWindowTo(String windowTo) { this.windowTo = windowTo; }
        public boolean isDryRun() { return dryRun; }
        public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    }
}
