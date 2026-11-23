package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.definition.ScenarioDefinitionDTO;
import com.chinacreator.gzcm.workspace.definition.ScenarioDefinitionService;
import com.chinacreator.gzcm.workspace.definition.ScenarioDefinitionVO;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import com.chinacreator.gzcm.workspace.scenario.MigrationPermissionChecker;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 场景定义聚合面（2026-11-21 场景工作台简化 v0.3）。
 *
 * <pre>
 * GET  /api/v1/workspace/scenarios/{id}/definition          — 定义聚合读
 * PUT  /api/v1/workspace/scenarios/{id}/definition          — 保存草稿（场景+绑定+蓝图）
 * POST /api/v1/workspace/scenarios/{id}/definition/publish  — 校验并发布
 * </pre>
 *
 * <p>独立 Controller：不向 {@code ScenarioController} / {@code ScenarioBlueprintController}
 * 增加方法，避免撞 {@code OpenApiPathParityTest} 的 12 条契约属主闭包。路径在
 * {@code /api/v1/workspace/**} 下，网关 KEEP 直传；鉴权默认 DENY + L1 准入，无 permitAll。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ScenarioDefinitionController {

    private final ScenarioDefinitionService definitionService;
    private final ScenarioReadGuard readGuard;
    private final MigrationPermissionChecker permissionChecker;

    public ScenarioDefinitionController(ScenarioDefinitionService definitionService,
                                        ScenarioReadGuard readGuard,
                                        MigrationPermissionChecker permissionChecker) {
        this.definitionService = definitionService;
        this.readGuard = readGuard;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping("/{id}/definition")
    public ApiResponse<ScenarioDefinitionVO> get(@PathVariable("id") String scenarioId) {
        readGuard.assertReadable("scenario_definition");
        return ApiResponse.success(ScenarioDefinitionVO.of(definitionService.get(scenarioId)));
    }

    @PutMapping("/{id}/definition")
    public ApiResponse<ScenarioDefinitionVO> save(@PathVariable("id") String scenarioId,
                                                  @RequestBody ScenarioDefinitionDTO dto) {
        assertAllowed("scenario:write");
        return ApiResponse.success(ScenarioDefinitionVO.of(definitionService.save(scenarioId, dto)));
    }

    @PostMapping("/{id}/definition/publish")
    public ApiResponse<ScenarioDefinitionVO> publish(@PathVariable("id") String scenarioId,
                                                     @RequestBody(required = false) ScenarioDefinitionDTO dto) {
        assertAllowed("scenario:publish");
        return ApiResponse.success(ScenarioDefinitionVO.of(definitionService.publish(scenarioId, dto)));
    }

    private void assertAllowed(String permissionPoint) {
        String subject = currentSubject();
        if (!permissionChecker.isAllowed(permissionPoint, subject)) {
            throw new ForbiddenOperationException(permissionPoint);
        }
    }

    private String currentSubject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            return String.valueOf(auth.getPrincipal());
        }
        return "anonymous";
    }
}
