package com.chinacreator.gzcm.workspace.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.CompletenessVO;
import com.chinacreator.gzcm.workspace.scenario.ScenarioCompletenessService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioSaveDTO;
import com.chinacreator.gzcm.workspace.scenario.ScenarioService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioStatusTransitionService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioVO;
import io.swagger.v3.oas.annotations.Operation;

/**
 * Scenario CRUD REST API — 业务场景管理（PMO-50 实体化，替代原内存 Map 实现）。
 *
 * <p>路径契约保持不变（只增不改）：
 * <pre>
 * GET    /api/v1/workspace/scenarios           — 场景列表
 * GET    /api/v1/workspace/scenarios/{id}      — 场景详情（含绑定+指标聚合）
 * POST   /api/v1/workspace/scenarios           — 创建场景
 * PUT    /api/v1/workspace/scenarios/{id}      — 更新场景
 * DELETE /api/v1/workspace/scenarios/{id}      — 删除场景（逻辑删除）
 * GET    /api/v1/workspace/scenarios/{id}/bindings — 场景绑定关系
 * </pre></p>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ScenarioController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioController.class);

    private final ScenarioService scenarioService;
    private final ScenarioCompletenessService completenessService;
    private final ScenarioStatusTransitionService statusTransitionService;

    public ScenarioController(ScenarioService scenarioService,
                              ScenarioCompletenessService completenessService,
                              ScenarioStatusTransitionService statusTransitionService) {
        this.scenarioService = scenarioService;
        this.completenessService = completenessService;
        this.statusTransitionService = statusTransitionService;
    }

    /** 场景列表。 */
    @GetMapping
    public ApiResponse<List<ScenarioVO>> list(
            @RequestParam(value = "purpose", required = false) String purpose) {
        // F07-14 R-27① 过滤口径：purpose=forecast → 只回 status=ACTIVE 且 islands 为空
        return ApiResponse.success(scenarioService.list(purpose));
    }

    /** 场景详情（含绑定 + 指标聚合）。 */
    @GetMapping("/{id}")
    public ApiResponse<ScenarioVO> get(@PathVariable String id) {
        return ApiResponse.success(scenarioService.get(id));
    }

    /** 创建场景（含可选绑定）。 */
    @PostMapping
    public ApiResponse<ScenarioVO> create(@RequestBody ScenarioSaveDTO dto) {
        return ApiResponse.success(scenarioService.create(dto));
    }

    /** 更新场景（null 字段不改动；bindings 非 null 时全量替换）。 */
    @PutMapping("/{id}")
    public ApiResponse<ScenarioVO> update(@PathVariable String id, @RequestBody ScenarioSaveDTO dto) {
        return ApiResponse.success(scenarioService.update(id, dto));
    }

    /** 逻辑删除场景。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        scenarioService.delete(id);
        return ApiResponse.success();
    }

    /** 场景绑定关系（六类分组）。 */
    @GetMapping("/{id}/bindings")
    public ApiResponse<Map<String, List<String>>> bindings(@PathVariable String id) {
        return ApiResponse.success(scenarioService.bindings(id));
    }

    /**
     * N1 完整度（详细设计-07 D-2 / F07-02）：连边覆盖率后端单源，前端只消费不重算。
     * 场景不存在 → 404（advice）；空场景 coverage=null 且 verdict=EMPTY_SCENARIO。
     */
    @Operation(summary = "getScenarioCompleteness")
    @GetMapping("/{id}/completeness")
    public ApiResponse<CompletenessVO> completeness(@PathVariable String id) {
        return ApiResponse.success(completenessService.computeFor(id));
    }

    /**
     * N12 状态迁移（详细设计-07 D-2 / F07-07 C-153）。
     * 非法迁移 → 409 ILLEGAL_TRANSITION；→COMPLETED 无正式运行 → 409 NO_FORMAL_RUN；
     * →ACTIVE 存在孤岛 → 409 ISLAND_BINDING。服务端下发 allowedTransitions。
     */
    @Operation(summary = "transitionScenarioStatus")
    @PatchMapping("/{id}/status")
    public ApiResponse<ScenarioStatusTransitionService.StatusVO> transitionStatus(
            @PathVariable String id, @RequestBody StatusRequest req) {
        return ApiResponse.success(statusTransitionService.transition(id,
                req == null ? null : req.getStatus()));
    }

    /** N12 入参：目标状态（必填）。 */
    public static class StatusRequest {
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
