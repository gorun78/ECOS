package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveBlueprintLayerVO;
import com.chinacreator.gzcm.engine.cognitive2.service.blueprint.CognitiveBlueprintService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认知六层蓝图聚合端点（D151）。
 *
 * <p><b>背景</b>：前端 {@code #/mission_control}（{@code CognitiveOperatingSystem.tsx}）经
 * {@code apiCognitiveBlueprint()} 请求 {@code GET /api/v1/cognitive/blueprint} 拉六层健康度，
 * 但该端点后端长期缺失（live 实测 404）⇒ 页面恒降级为「示意数据」。本 Controller 补上真实聚合腿。</p>
 *
 * <p><b>契约</b>：返回 {@code ApiResponse<List<CognitiveBlueprintLayerVO>>}，每层带
 * {@code id/nameZh/sourceEngine/coverage/verified/components/reason}。前端
 * {@code mapApiBlueprintLayer} 按 {@code id/nameZh/coverage} 映射；{@code verified=false} 的层
 * coverage 恒 0，前端据此保持降级语义（不把探活失败当真实覆盖）。</p>
 *
 * <p><b>与既有端点不冲突</b>：本路径 {@code /api/v1/cognitive/blueprint} 为新增；
 * {@code /api/v1/cognitive/config} 归 ai-engine 与 cognitive-engine 各自控制器，
 * {@code /api/v1/engine/cognitive/health} 归 {@link CognitiveEngineOpenHealthController}，均不同路径。</p>
 *
 * @author ecos-factory
 * @since D151 (2026-11-16)
 */
@RestController
@RequestMapping("/api/v1/cognitive")
public class CognitiveBlueprintController {

    private final CognitiveBlueprintService blueprintService;

    public CognitiveBlueprintController(CognitiveBlueprintService blueprintService) {
        this.blueprintService = blueprintService;
    }

    /**
     * GET /api/v1/cognitive/blueprint — 聚合六层蓝图健康度。
     *
     * <p>逐层隔离探活，单层失败只置该层 verified=false，不影响其余层，整体恒 200。</p>
     */
    @GetMapping("/blueprint")
    public ApiResponse<List<CognitiveBlueprintLayerVO>> blueprint() {
        return ApiResponse.success(blueprintService.aggregate());
    }
}
