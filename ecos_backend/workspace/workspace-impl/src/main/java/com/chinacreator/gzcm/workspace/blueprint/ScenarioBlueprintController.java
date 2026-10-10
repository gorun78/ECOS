package com.chinacreator.gzcm.workspace.blueprint;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 场景蓝图 REST（补充设计 v1.0 §6 + §13 清单，§6.1 属主 workspace/场景工作台）。
 *
 * <p>路径域 = 既有场景前缀嵌套（仓内既有 {@code /api/v1/business/scenarios}（认知四端点）与
 * {@code /api/v1/workspace/scenarios} 均存在；本组取 cognition 同款 business 场景前缀嵌套，
 * 只增不改既有前缀，与前端 cognitiveClient 既有契约同源）。
 * 三滤波器：/api/v1/ 前缀走 sysman L1 准入（ClearanceInterceptor 默认规则），
 * 未登记匿名面即为默认 DENY（fail-closed）。</p>
 *
 * <p>承重口径：Controller 只做路径映射 + 主体解析，重定向 {@link ScenarioBlueprintService}；
 * 异常语义由 {@code WorkspaceExceptionHandler}（ScenarioDomainException 子类树覆盖本包全部
 * 领域异常）统一映射：非法迁移/门槛未过/并发冲突 409，不存在 404，内容非法 400。</p>
 */
@RestController
@RequestMapping("/api/v1/business/scenarios/{scenarioId}/blueprints")
public class ScenarioBlueprintController {

    private final ScenarioBlueprintService blueprintService;

    public ScenarioBlueprintController(ScenarioBlueprintService blueprintService) {
        this.blueprintService = blueprintService;
    }

    /** POST 创建草稿（§6.2 逻辑模型整体入参，DRAFT version+1 新行）。 */
    @PostMapping
    public ResponseEntity<ApiResponse<ScenarioBlueprintVO>> create(
            @PathVariable("scenarioId") String scenarioId,
            @RequestBody BlueprintSaveDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(blueprintService.create(scenarioId, dto, currentSubject())));
    }

    /** GET 场景蓝图版本表（升序）。 */
    @GetMapping
    public ApiResponse<List<ScenarioBlueprintVO>> list(@PathVariable("scenarioId") String scenarioId) {
        return ApiResponse.success(blueprintService.listVersions(scenarioId));
    }

    /** POST {id}/transition 状态迁移（§6.3 五要素留痕；非法 409）。 */
    @PostMapping("/{id}/transition")
    public ApiResponse<ScenarioBlueprintVO> transition(@PathVariable("scenarioId") String scenarioId,
                                                        @PathVariable("id") String blueprintId,
                                                        @RequestBody Map<String, Object> body) {
        String target = stringOrNull(body.get("target"));
        String reason = stringOrNull(body.get("reason"));
        return ApiResponse.success(blueprintService.transition(blueprintId, target, reason, currentSubject()));
    }

    /** 修订（§6.2 版本不可变语义）：以本版本为模板生成 version+1 的 DRAFT 新行。 */
    @PostMapping("/{id}/revise")
    public ResponseEntity<ApiResponse<ScenarioBlueprintVO>> revise(
            @PathVariable("scenarioId") String scenarioId,
            @PathVariable("id") String blueprintId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(blueprintService.revise(blueprintId, currentSubject())));
    }

    /** GET {id} 蓝图详情（含 allowedTransitions 前端按钮态）。 */
    @GetMapping("/{id}")
    public ApiResponse<ScenarioBlueprintVO> get(@PathVariable("scenarioId") String scenarioId,
                                                 @PathVariable("id") String blueprintId) {
        return ApiResponse.success(blueprintService.get(blueprintId));
    }

    /** GET {id}/validate 发布前四道静态门槛（§6.6；只读，200 + passed 明细）。 */
    @GetMapping("/{id}/validate")
    public ApiResponse<Map<String, Object>> validate(@PathVariable("scenarioId") String scenarioId,
                                                      @PathVariable("id") String blueprintId) {
        return ApiResponse.success(blueprintService.validate(blueprintId));
    }

    /** GET {id}/diff 版本 diff（无 diff 表，§6.2 两版本 gap/依赖/content 结构化对比）。 */
    @GetMapping("/{id}/diff")
    public ApiResponse<Map<String, Object>> diff(@PathVariable("scenarioId") String scenarioId,
                                                  @PathVariable("id") String blueprintId,
                                                  @RequestParam(name = "fromVersion", required = false) Integer fromVersion,
                                                  @RequestParam(name = "toVersion", required = false) Integer toVersion) {
        return ApiResponse.success(blueprintService.diff(blueprintId, fromVersion, toVersion));
    }

    /** POST {id}/approve 审批（approval_ref 缺失 409；审批链不齐拒）。 */
    @PostMapping("/{id}/approve")
    public ApiResponse<ScenarioBlueprintVO> approve(@PathVariable("scenarioId") String scenarioId,
                                                     @PathVariable("id") String blueprintId,
                                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.success(blueprintService.approve(
                blueprintId, stringOrNull(body.get("approvalRef")), currentSubject()));
    }

    /** POST {id}/release 发布（验收门槛全过才 201；发布版本不可变，SUP-BP-02）。 */
    @PostMapping("/{id}/release")
    public ResponseEntity<ApiResponse<ScenarioBlueprintVO>> release(
            @PathVariable("scenarioId") String scenarioId,
            @PathVariable("id") String blueprintId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "发布版本不可变（§6.6），traceId=" + EcosTraceContext.currentTraceId(),
                        blueprintService.release(blueprintId, currentSubject())));
    }

    /** 迁移目标态快捷面（同 transition，前端可显式目标名 POST；与 target 等价仅多一层入口）。 */
    @PostMapping("/{id}/transition/{target}")
    public ApiResponse<ScenarioBlueprintVO> transitionTo(@PathVariable("scenarioId") String scenarioId,
                                                          @PathVariable("id") String blueprintId,
                                                          @PathVariable("target") String target,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : stringOrNull(body.get("reason"));
        return ApiResponse.success(blueprintService.transition(blueprintId, target, reason, currentSubject()));
    }

    /** 审批门：验收门槛全过才进 READY_FOR_APPROVAL（§6.6；门槛未过 409）。 */
    @PostMapping("/{id}/enter-approval")
    public ApiResponse<ScenarioBlueprintVO> enterApproval(@PathVariable("scenarioId") String scenarioId,
                                                           @PathVariable("id") String blueprintId,
                                                           @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : stringOrNull(body.get("reason"));
        return ApiResponse.success(blueprintService.enterApprovalGate(blueprintId, reason, currentSubject()));
    }

    /** 主体只取 SecurityContext（JWT 链路注入）；禁读请求体 userId（与场景状态迁移同纪律）。 */
    private static String currentSubject() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "anonymous";
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
