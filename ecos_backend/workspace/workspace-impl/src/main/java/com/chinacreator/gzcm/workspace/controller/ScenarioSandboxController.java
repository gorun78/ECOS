package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.SandboxLayoutVO;
import com.chinacreator.gzcm.workspace.scenario.SandboxSaveDTO;
import com.chinacreator.gzcm.workspace.scenario.ScenarioSandboxLayoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 场景沙盘布局 REST API — React Flow 画布持久化（PMO-60 v2.0 P1）。
 *
 * <p>路径契约：
 * <pre>
 * GET  /api/v1/workspace/scenarios/{id}/sandbox/layout — 获取布局（无记录返回默认）
 * POST /api/v1/workspace/scenarios/{id}/sandbox/layout — 保存布局（乐观锁，conflict 返 409）
 * </pre></p>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ScenarioSandboxController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioSandboxController.class);

    private final ScenarioSandboxLayoutService layoutService;

    public ScenarioSandboxController(ScenarioSandboxLayoutService layoutService) {
        this.layoutService = layoutService;
    }

    /** 获取场景沙盘布局（无记录返默认布局）。 */
    @GetMapping("/{id}/sandbox/layout")
    public ApiResponse<SandboxLayoutVO> getLayout(@PathVariable String id) {
        return ApiResponse.success(layoutService.getLayout(id));
    }

    /**
     * 保存场景沙盘布局（乐观锁）。
     * body 含 expectedVersion；冲突返 ApiResponse{code:409, message:"version_conflict..."}。
     */
    @PostMapping("/{id}/sandbox/layout")
    public ApiResponse<?> saveLayout(@PathVariable String id,
                                      @RequestBody SandboxSaveDTO dto) {
        try {
            SandboxLayoutVO vo = layoutService.saveLayout(id, dto);
            return ApiResponse.success(vo);
        } catch (com.chinacreator.gzcm.common.exception.BusinessException e) {
            if (e.getErrorCode() == 409) {
                return ApiResponse.error(409, "version_conflict", e.getMessage());
            }
            throw e;
        }
    }
}
