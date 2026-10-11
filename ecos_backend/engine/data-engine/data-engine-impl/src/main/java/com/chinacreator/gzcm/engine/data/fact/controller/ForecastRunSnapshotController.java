package com.chinacreator.gzcm.engine.data.fact.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.fact.service.ForecastRunSnapshotService;
import com.chinacreator.gzcm.engine.data.model.ForecastRunSnapshotVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预测运行只读快照 REST 面 — 详细设计-09 v1.2 §十一 H-3 交付③（ST08 读通道，只增不改）。
 *
 * <p>{@code GET /api/v1/datanet/forecast-runs/{runId}/snapshot} =
 * cognitive 侧 {@code ForecastRunSnapshotReader} 的唯一取数通道（认知引擎禁直连
 * {@code ecos_dw}，V197 javadoc / 铁律 §0.6）。响应键名与 {@link ForecastRunSnapshotVO}
 * javadoc 映射表逐字对齐 RunSnapshot / Cell / ContributionEdgeSource 组件名。
 *
 * <p>语义：runId 无行 → 404（禁伪装空快照）；非 JSON 结果载荷 → cells/edges 空列表 +
 * 利润 null（禁伪造数值，明细缺失的 fail-loud 处置归认知侧 DG-9 口径）。
 * 只读端点：无写路径、无副作用。
 */
@RestController
@RequestMapping("/api/v1/datanet/forecast-runs")
public class ForecastRunSnapshotController {

    private final ForecastRunSnapshotService snapshotService;

    public ForecastRunSnapshotController(ForecastRunSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /** getForecastRunSnapshot — 六要素 + 金额 cell 明细 + 因果边明细 + 两利润真值（可空）。 */
    @GetMapping("/{runId}/snapshot")
    public ApiResponse<ForecastRunSnapshotVO> getForecastRunSnapshot(@PathVariable("runId") String runId) {
        return ApiResponse.success(snapshotService.getSnapshot(runId));
    }
}
