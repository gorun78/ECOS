package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.config.ScenarioAlgorithmRequest;
import com.chinacreator.gzcm.workspace.config.ScenarioAlgorithmVO;
import com.chinacreator.gzcm.workspace.config.ScenarioConfigService;
import com.chinacreator.gzcm.workspace.config.ScenarioTemplateRequest;
import com.chinacreator.gzcm.workspace.config.ScenarioTemplateVO;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import com.chinacreator.gzcm.workspace.scenario.MigrationPermissionChecker;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 场景配置面（模板/算法库，2026-11-21 场景工作台简化 v0.3）。
 *
 * <pre>
 * GET/POST/PUT/DELETE /api/v1/workspace/scenario-templates
 * GET/POST/PUT/DELETE /api/v1/workspace/algorithms
 * </pre>
 *
 * <p>边界出入参强类型（DoD：控制器字段面不新增弱类型载荷；内部字典转换留在 record 内）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class ScenarioConfigController {

    private final ScenarioConfigService configService;
    private final ScenarioReadGuard readGuard;
    private final MigrationPermissionChecker permissionChecker;

    public ScenarioConfigController(ScenarioConfigService configService,
                                    ScenarioReadGuard readGuard,
                                    MigrationPermissionChecker permissionChecker) {
        this.configService = configService;
        this.readGuard = readGuard;
        this.permissionChecker = permissionChecker;
    }

    // ── 场景模板 ───────────────────────────────────────────────

    @GetMapping("/scenario-templates")
    public ApiResponse<List<ScenarioTemplateVO>> listTemplates() {
        readGuard.assertReadable("scenario_template");
        return ApiResponse.success(ScenarioTemplateVO.listOf(configService.listTemplates()));
    }

    @GetMapping("/scenario-templates/{id}")
    public ApiResponse<ScenarioTemplateVO> getTemplate(@PathVariable String id) {
        readGuard.assertReadable("scenario_template");
        return ApiResponse.success(ScenarioTemplateVO.of(configService.getTemplate(id)));
    }

    @PostMapping("/scenario-templates")
    public ApiResponse<ScenarioTemplateVO> createTemplate(@RequestBody ScenarioTemplateRequest dto) {
        assertWrite();
        return ApiResponse.success(ScenarioTemplateVO.of(
                configService.saveTemplate(dto.toMap(), currentSubject())));
    }

    @PutMapping("/scenario-templates/{id}")
    public ApiResponse<ScenarioTemplateVO> updateTemplate(@PathVariable String id,
                                                          @RequestBody ScenarioTemplateRequest dto) {
        assertWrite();
        return ApiResponse.success(ScenarioTemplateVO.of(
                configService.saveTemplate(dto.withId(id), currentSubject())));
    }

    @DeleteMapping("/scenario-templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable String id) {
        assertWrite();
        configService.deleteTemplate(id, currentSubject());
        return ApiResponse.success();
    }

    // ── 算法库 ─────────────────────────────────────────────────

    @GetMapping("/algorithms")
    public ApiResponse<List<ScenarioAlgorithmVO>> listAlgorithms() {
        readGuard.assertReadable("scenario_algorithm");
        return ApiResponse.success(ScenarioAlgorithmVO.listOf(configService.listAlgorithms()));
    }

    @GetMapping("/algorithms/{id}")
    public ApiResponse<ScenarioAlgorithmVO> getAlgorithm(@PathVariable String id) {
        readGuard.assertReadable("scenario_algorithm");
        return ApiResponse.success(ScenarioAlgorithmVO.of(configService.getAlgorithm(id)));
    }

    @PostMapping("/algorithms")
    public ApiResponse<ScenarioAlgorithmVO> createAlgorithm(@RequestBody ScenarioAlgorithmRequest dto) {
        assertWrite();
        return ApiResponse.success(ScenarioAlgorithmVO.of(
                configService.saveAlgorithm(dto.toMap(), currentSubject())));
    }

    @PutMapping("/algorithms/{id}")
    public ApiResponse<ScenarioAlgorithmVO> updateAlgorithm(@PathVariable String id,
                                                            @RequestBody ScenarioAlgorithmRequest dto) {
        assertWrite();
        return ApiResponse.success(ScenarioAlgorithmVO.of(
                configService.saveAlgorithm(dto.withId(id), currentSubject())));
    }

    @DeleteMapping("/algorithms/{id}")
    public ApiResponse<Void> deleteAlgorithm(@PathVariable String id) {
        assertWrite();
        configService.deleteAlgorithm(id, currentSubject());
        return ApiResponse.success();
    }

    private void assertWrite() {
        if (!permissionChecker.isAllowed("scenario:write", currentSubject())) {
            throw new ForbiddenOperationException("scenario:write");
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
