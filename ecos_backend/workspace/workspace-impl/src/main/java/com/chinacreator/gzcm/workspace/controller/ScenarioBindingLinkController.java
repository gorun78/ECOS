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
import io.swagger.v3.oas.annotations.Operation;

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
 * <p>【校订 2026-10-02，详细设计-07 F07-01-5 / W165 / C147】承流与鉴权接线（三滤波器第①条
 * {@code VersionPrefixRewriteFilter} 双向映射 + gateway 反向代理路由 + :18090 态与 gateway 等价的
 * Security 链 + 两态鉴权等价）尚未完成，gateway 现以 REGEX 排除本 Controller，:18090 态尚未装
 * JWT 校验——**不得视为已放行**。接线落点与范围详见 详细设计-07 §F07-01，勿在此声称接线已完成。</p>
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
    @Operation(operationId = "getScenarioGraph", summary = "getScenarioGraph")
    @GetMapping("/{id}/graph")
    public ApiResponse<ScenarioGraphVO> graph(@PathVariable String id) {
        return ApiResponse.success(linkService.graph(id));
    }

    /** 边列表（六类节点间可组成的边）。 */
    @Operation(operationId = "listBindingLinks", summary = "listBindingLinks")
    @GetMapping("/{id}/binding-links")
    public ApiResponse<List<ScenarioBindingLinkVO>> list(@PathVariable String id) {
        return ApiResponse.success(linkService.listLinks(id));
    }

    /** 新增一条边（§0.6.2.1/§0.6.2.2 校验 + 孤岛校验）。 */
    @Operation(operationId = "saveBindingLink", summary = "saveBindingLink")
    @PostMapping("/{id}/binding-links")
    public ApiResponse<ScenarioBindingLinkVO> save(@PathVariable String id,
                                                   @RequestBody ScenarioBindingLinkSaveDTO dto) {
        log.info("新增场景边 scenario={} type={} src={} tgt={}",
                id, dto.getLinkType(), dto.getSourceBindingId(), dto.getTargetBindingId());
        return ApiResponse.success(linkService.save(id, dto));
    }

    /** 逻辑删除一条边。 */
    @Operation(operationId = "deleteBindingLink", summary = "deleteBindingLink")
    @DeleteMapping("/{id}/binding-links/{linkId}")
    public ApiResponse<Void> delete(@PathVariable String id, @PathVariable String linkId) {
        linkService.delete(id, linkId);
        return ApiResponse.success();
    }
}
