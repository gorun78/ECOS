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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 *
 * <p><b>状态行保真（#142/B4-3e-32，收口裸回面 D-B4-59 在本类的类型见证腿）</b>：
 * {@code generate}／{@code publish}（含其私有 helper {@code mapMessage}）原先返裸
 * {@code ApiResponse}——该类只描述信封、不带 HTTP 状态能力，Spring 落的状态行恒 <b>HTTP 200</b>，
 * 于是 D.3 契约里的「503 runtime-task 不可用／403 非知识管理员／409 非法跃迁与 I-6／404 记录不存在」
 * 在传输层与成功同形（§4.7.2「不可用 ≠ 无权限」在状态行上无从判别）。现逐枚腿经
 * {@code ResponseEntity.status(...)} 落真状态行，<b>状态值与机读码同源取 {@link KbErrorCodes}
 * 的 {@code KbError} 记录</b>（{@code httpStatus()} 与 {@code code()} 成对，禁在 Controller 里
 * 另写一遍数字），其余腿取对应 {@link HttpStatus}；成功腿显式 {@code ResponseEntity.ok(...)}
 * 保持 200。<b>路径、入参、信封字段与签名三样零改动</b>（API 只增不改：只补状态行，
 * 同 D-B4-59 属主端点先例）。{@code list}／{@code resolve} 本批未动——不含拒绝态腿，
 * 属非拒绝态裸回登记面（判据 B 只减不增），改判随该面另批。</p>
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> generate(@RequestBody ProfileGenerateRequest req) {
        if (req == null || req.getMetricCodes() == null || req.getMetricCodes().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.badRequest("metricCodes 不能为空"));
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
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>success("已提交", out)
                    .withTraceId((String) out.get("traceId")));
        } catch (TaskManagementException | RuntimeException e) {
            log.error("画像生成提交失败: {}", e.getMessage(), e);
            return statusOf(KbErrorCodes.TASK_BASE_UNAVAILABLE,
                    ApiResponse.<Map<String, Object>>error(
                            KbErrorCodes.TASK_BASE_UNAVAILABLE.httpStatus(),
                            KbErrorCodes.TASK_BASE_UNAVAILABLE.code(),
                            "runtime-task 不可用，禁降级为本地同步计算: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<KbProfileDO>> publish(@PathVariable String id,
                                            @RequestHeader(value = ROLE_HEADER, required = false) String role,
                                            @RequestHeader(value = "X-Ecos-User", required = false)
                                            String user) {
        String actor = (user == null || user.isBlank()) ? role : user;
        if (role == null || !role.contains(ROLE_KB_ADMIN)) {
            return statusOf(KbErrorCodes.PERMISSION_DENIED,
                    ApiResponse.<KbProfileDO>error(
                            KbErrorCodes.PERMISSION_DENIED.httpStatus(),
                            KbErrorCodes.PERMISSION_DENIED.code(),
                            "权限不足：需 knowledge_admin 角色"));
        }
        try {
            KbProfileDO row = service.publish(id, actor);
            return ResponseEntity.ok(ApiResponse.<KbProfileDO>success(row).withTraceIdFromMdc());
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

    /**
     * GET /stock → 画像存量两计（只读，WAgent Readiness 定级 {@code know.sample_size}
     * 度量取数端点；业务数据面，默认 authenticated，不登记匿名面）。
     *
     * <p>查询参数均可选：{@code metricCode} / {@code domain}（逗号分隔多枚）/
     * {@code status}（缺省 {@code PUBLISHED}，默认值在本层承载，禁写死 SQL）。
     * 字面段 {@code /stock} 与 {@code /resolve} 等字面路径并存（无 {@code {profileKey}}
     * 单段 GET 映射，字面优先恒成立，无需改挂 engine 命名空间）。
     * 响应 data 段键面固定：profiles / sampleSum / statusFilter / metricCode / domain / evidence。
     */
    @GetMapping("/stock")
    public ApiResponse<Map<String, Object>> stock(
            @RequestParam(value = "metricCode", required = false) String metricCode,
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "status", defaultValue = "PUBLISHED") String status) {
        return ApiResponse.<Map<String, Object>>success(
                        service.profileStock(status, metricCode, domain))
                .withTraceIdFromMdc();
    }

    // ── 领域异常 → ApiResponse 映射 ──────────────────────────────────────

    private ResponseEntity<ApiResponse<KbProfileDO>> mapMessage(IllegalStateException e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains(KbErrorCodes.PUBLISHED_IMMUTABLE.code())) {
            return statusOf(KbErrorCodes.PUBLISHED_IMMUTABLE,
                    ApiResponse.<KbProfileDO>error(KbErrorCodes.PUBLISHED_IMMUTABLE.httpStatus(),
                            KbErrorCodes.PUBLISHED_IMMUTABLE.code(), m));
        }
        if (m.contains(KbErrorCodes.STATUS_TRANSITION_INVALID.code())) {
            return statusOf(KbErrorCodes.STATUS_TRANSITION_INVALID,
                    ApiResponse.<KbProfileDO>error(KbErrorCodes.STATUS_TRANSITION_INVALID.httpStatus(),
                            KbErrorCodes.STATUS_TRANSITION_INVALID.code(), m));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<KbProfileDO>notFound(m));
    }

    /**
     * 状态行与信封码同源：两者都取 {@link KbErrorCodes} 的同一枚 {@code KbError}，
     * 避免「body 写 409、状态行另写一遍 409」的双源漂移（改一处不改另一处即假保真）。
     */
    private static <T> ResponseEntity<ApiResponse<T>> statusOf(KbErrorCodes.KbError error,
                                                               ApiResponse<T> body) {
        return ResponseEntity.status(HttpStatus.valueOf(error.httpStatus())).body(body);
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
