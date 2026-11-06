package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.framework.CognitiveFrameworkDefinition;
import com.chinacreator.gzcm.engine.cognitive2.service.framework.CognitiveFrameworkRegistryService;
import com.chinacreator.gzcm.engine.cognitive2.service.framework.FrameworkSelectionService;
import com.chinacreator.gzcm.engine.cognitive2.service.framework.PlanValidator;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
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
 * 认知框架注册与策略选择 REST（详细设计-认知引擎 v1.0 §5/§6；只增不改既有端点）。
 *
 * <pre>
 * GET  /api/v1/cognitive/frameworks                          — 列表（problemType/domain/status 过滤）
 * GET  /api/v1/cognitive/frameworks/{frameworkId}/{version}   — 版本详情
 * POST /api/v1/cognitive/frameworks                          — 注册 DRAFT（同版本重复 409）
 * POST /api/v1/cognitive/frameworks/{frameworkId}/{version}/publish   — 发布（不可变）
 * POST /api/v1/cognitive/frameworks/{frameworkId}/{version}/status    — 治理迁移
 * POST /api/v1/cognitive/frameworks/select                   — 策略选择（硬约束→排序→DAG）
 * POST /api/v1/cognitive/plans/validate                      — 计划校验
 * </pre>
 */
@RestController
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class CognitiveFrameworkController {

    private final CognitiveFrameworkRegistryService registry;
    private final FrameworkSelectionService selectionService;
    private final PlanValidator planValidator;

    public CognitiveFrameworkController(CognitiveFrameworkRegistryService registry,
                                        FrameworkSelectionService selectionService,
                                        PlanValidator planValidator) {
        this.registry = registry;
        this.selectionService = selectionService;
        this.planValidator = planValidator;
    }

    @GetMapping(CognitiveApiPaths.FRAMEWORKS_REL)
    public ApiResponse<List<CognitiveFrameworkDefinition>> list(
            @RequestParam(required = false) String problemType,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(registry.list(problemType, domain, status));
    }

    @GetMapping(CognitiveApiPaths.FRAMEWORK_ITEM_REL)
    public ApiResponse<CognitiveFrameworkDefinition> get(
            @PathVariable(CognitiveApiPaths.PARAM_FRAMEWORK_ID) String frameworkId,
            @PathVariable(CognitiveApiPaths.PARAM_FRAMEWORK_VERSION) String version) {
        return ApiResponse.success(registry.get(frameworkId, version));
    }

    @PostMapping(CognitiveApiPaths.FRAMEWORKS_REL)
    public ApiResponse<CognitiveFrameworkDefinition> register(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(registry.register(body, actor()));
    }

    @PostMapping(CognitiveApiPaths.FRAMEWORK_PUBLISH_REL)
    public ApiResponse<CognitiveFrameworkDefinition> publish(
            @PathVariable(CognitiveApiPaths.PARAM_FRAMEWORK_ID) String frameworkId,
            @PathVariable(CognitiveApiPaths.PARAM_FRAMEWORK_VERSION) String version) {
        return ApiResponse.success(registry.publish(frameworkId, version, actor()));
    }

    @PostMapping(CognitiveApiPaths.FRAMEWORK_STATUS_REL)
    public ApiResponse<CognitiveFrameworkDefinition> transition(
            @PathVariable(CognitiveApiPaths.PARAM_FRAMEWORK_ID) String frameworkId,
            @PathVariable(CognitiveApiPaths.PARAM_FRAMEWORK_VERSION) String version,
            @RequestBody(required = false) Map<String, Object> body) {
        Object target = body == null ? null : body.get("targetStatus");
        if (target == null || String.valueOf(target).isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "targetStatus");
        }
        return ApiResponse.success(registry.transition(frameworkId, version, String.valueOf(target), actor()));
    }

    @PostMapping(CognitiveApiPaths.FRAMEWORK_SELECT_REL)
    public ApiResponse<Map<String, Object>> select(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(selectionService.select(body));
    }

    @PostMapping(CognitiveApiPaths.PLANS_VALIDATE_REL)
    public ApiResponse<Map<String, Object>> validatePlan(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(planValidator.validate(body));
    }

    private static String actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? "system" : auth.getName();
    }
}
