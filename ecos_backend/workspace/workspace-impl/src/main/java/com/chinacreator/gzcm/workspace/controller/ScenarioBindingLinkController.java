package com.chinacreator.gzcm.workspace.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.ScenarioBindingLinkService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioBindingLinkService.ScenarioBindingLinkSaveDTO;
import com.chinacreator.gzcm.workspace.scenario.ScenarioBindingLinkService.ScenarioBindingLinkVO;
import com.chinacreator.gzcm.workspace.scenario.ScenarioBindingLinkService.ScenarioGraphVO;

/**
 * 场景绑定关系边 REST API — 架构铁律 §0.6.2 子图化（PMO-66 A4）。
 *
 * <p>路径契约：
 * <pre>
 * GET    /api/v1/workspace/scenarios/{id}/graph          — 完整图 + 覆盖率
 * GET    /api/v1/workspace/scenarios/{id}/binding-links  — 所有边
 * POST   /api/v1/workspace/scenarios/{id}/binding-links  — 新增边
 * DELETE /api/v1/workspace/scenarios/{id}/binding-links/{linkId} — 逻辑删边
 * </pre></p>
 *
 * <p>三滤波器：{@code /api/v1/workspace/**} 已由 SecurityConfig + ClearanceInterceptor 放行。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ScenarioBindingLinkController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioBindingLinkController.class);

    private final ScenarioBindingLinkService linkService;

    public ScenarioBindingLinkController(ScenarioBindingLinkService linkService) {
        this.linkService = linkService;
    }

    /** 场景完整图（nodes + links + coverage）— 前端 /graph 消费（B/B3 用）。 */
    @GetMapping("/{id}/graph")
    public ApiResponse<ScenarioGraphVO> graph(@PathVariable String id) {
        return ApiResponse.success(linkService.graph(id));
    }

    /** 边列表（六类节点间可组成的边）。 */
    @GetMapping("/{id}/binding-links")
    public ApiResponse<List<ScenarioBindingLinkVO>> list(@PathVariable String id) {
        return ApiResponse.success(linkService.listLinks(id));
    }

    /** 新增一条边（§0.6.2.1/§0.6.2.2 校验 + 孤岛校验）。 */
    @PostMapping("/{id}/binding-links")
    public ApiResponse<ScenarioBindingLinkVO> save(@PathVariable String id,
                                                   @RequestBody ScenarioBindingLinkSaveDTO dto) {
        log.info("新增场景边 scenario={} type={} src={} tgt={}",
                id, dto.getLinkType(), dto.getSourceBindingId(), dto.getTargetBindingId());
        return ApiResponse.success(linkService.save(id, dto));
    }

    /** 逻辑删除一条边。 */
    @DeleteMapping("/{id}/binding-links/{linkId}")
    public ApiResponse<Void> delete(@PathVariable String id, @PathVariable String linkId) {
        linkService.delete(id, linkId);
        return ApiResponse.success();
    }
}
