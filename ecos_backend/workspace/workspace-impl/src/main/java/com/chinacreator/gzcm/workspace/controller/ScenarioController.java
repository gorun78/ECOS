package com.chinacreator.gzcm.workspace.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.BindingNode;
import com.chinacreator.gzcm.workspace.scenario.CompletenessDataLoader;
import com.chinacreator.gzcm.workspace.scenario.CompletenessReport;
import com.chinacreator.gzcm.workspace.scenario.LinkEdge;
import com.chinacreator.gzcm.workspace.scenario.ScenarioCompletenessService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioSaveDTO;
import com.chinacreator.gzcm.workspace.scenario.ScenarioService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioStatusTransitionService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioVO;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;

/**
 * Scenario CRUD REST API — 业务场景管理（PMO-50 实体化，替代原内存 Map 实现）。
 *
 * <p>路径契约保持不变（只增不改）：
 * <pre>
 * GET    /api/v1/workspace/scenarios           — 场景列表（附加 {@code ?purpose=forecast} 过滤，F07-14）
 * GET    /api/v1/workspace/scenarios/{id}      — 场景详情（附加 allowedTransitions，F07-07）
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
    private final ScenarioStatusTransitionService transitionService;
    private final CompletenessDataLoader loader;
    private final ScenarioCompletenessService completenessService;
    private final ScenarioReadGuard readGuard;

    public ScenarioController(ScenarioService scenarioService,
                              ScenarioStatusTransitionService transitionService,
                              CompletenessDataLoader loader,
                              ScenarioCompletenessService completenessService,
                              ScenarioReadGuard readGuard) {
        this.scenarioService = scenarioService;
        this.transitionService = transitionService;
        this.loader = loader;
        this.completenessService = completenessService;
        this.readGuard = readGuard;
    }

    /**
     * 场景列表。附加 {@code ?purpose=forecast} 只返回 ACTIVE 且无孤岛的场景（口径同 F07-04-5，
     * 不改既有签名，无参调用行为不变）。
     */
    @GetMapping
    public ApiResponse<List<ScenarioVO>> list(@RequestParam(name = "purpose", required = false) String purpose) {
        readGuard.assertReadable("scenario_list");
        List<ScenarioVO> all = scenarioService.list();
        if (!"forecast".equalsIgnoreCase(purpose)) {
            return ApiResponse.success(all);
        }
        List<ScenarioVO> filtered = new ArrayList<>();
        for (ScenarioVO vo : all) {
            if (isActiveWithoutIsland(vo)) {
                filtered.add(vo);
            }
        }
        return ApiResponse.success(filtered);
    }

    /** 场景详情（含绑定 + 指标聚合 + allowedTransitions 附加字段）。 */
    @GetMapping("/{id}")
    public ApiResponse<ScenarioVO> get(@PathVariable String id) {
        readGuard.assertReadable("scenario_detail");
        ScenarioVO vo = scenarioService.get(id);
        vo.setAllowedTransitions(transitionService.allowedTransitionsFor(id));
        return ApiResponse.success(vo);
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
        readGuard.assertReadable("scenario_bindings");
        return ApiResponse.success(scenarioService.bindings(id));
    }

    private boolean isActiveWithoutIsland(ScenarioVO vo) {
        if (!"ACTIVE".equalsIgnoreCase(vo.getStatus())) {
            return false;
        }
        List<BindingNode> bindings = loader.bindingsOf(vo.getId());
        List<LinkEdge> links = loader.linksOf(vo.getId());
        CompletenessReport report = completenessService.compute(vo.getId(), bindings, links);
        return report.getIslands().isEmpty();
    }
}
