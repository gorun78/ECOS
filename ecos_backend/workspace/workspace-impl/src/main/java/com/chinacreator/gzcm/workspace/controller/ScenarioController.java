package com.chinacreator.gzcm.workspace.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.ScenarioSaveDTO;
import com.chinacreator.gzcm.workspace.scenario.ScenarioService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioVO;

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

    public ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    /** 场景列表。 */
    @GetMapping
    public ApiResponse<List<ScenarioVO>> list() {
        return ApiResponse.success(scenarioService.list());
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
}
