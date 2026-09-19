package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.dto.DataLayerRowsVO;
import com.chinacreator.gzcm.engine.data.model.DataLayer;
import com.chinacreator.gzcm.engine.data.service.DataLayerInstanceService;
import com.chinacreator.gzcm.engine.data.service.DataLayerService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据分层 Controller — 分层概览 / 按层列资源 / DW 层实例行读取。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET /api/v1/engine/data/layers                              — 分层概览</li>
 *   <li>GET /api/v1/engine/data/layers/{layer}                      — 按层列资源</li>
 *   <li>GET /api/v1/engine/data/layers/{layer}/resources/{id}/rows  — 实例行增量读取（水位线，PMO-B3-1）</li>
 *   <li>GET /api/v1/engine/data/layers/{layer}/resources/{id}/sample— 实例行抽样（dry-run，PMO-B3-1）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/engine/data/layers")
public class DataLayerController {

    private final DataLayerService dataLayerService;

    /** DW 层实例行读取服务（PMO-B3-1 T2）。 */
    private final DataLayerInstanceService dataLayerInstanceService;

    public DataLayerController(DataLayerService dataLayerService,
                               DataLayerInstanceService dataLayerInstanceService) {
        this.dataLayerService = dataLayerService;
        this.dataLayerInstanceService = dataLayerInstanceService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getLayerSummary() {
        Map<String, Object> summary = dataLayerService.getLayerSummary();
        return ApiResponse.success(summary);
    }

    @GetMapping("/{layer}")
    public ApiResponse<Map<String, Object>> getResourcesByLayer(@PathVariable String layer) {
        try {
            DataLayer.valueOf(layer);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest("Invalid layer: " + layer);
        }
        List<Map<String, Object>> resources = dataLayerService.getResourcesByLayer(layer);
        return ApiResponse.success(Map.of("layer", layer, "resources", resources, "total", resources.size()));
    }

    /**
     * 增量读取分层资源实例行（PMO-B3-1 T2）。
     *
     * @param layer     分层名（须为合法 DataLayer 枚举）
     * @param id        数据资源 ID（td_data_resource.resource_id）
     * @param watermark 上次读取水位（空 = 从头读）
     * @param limit     返回行数上限（空 = 默认 1000，硬上限 5000）
     * @return 实例行结果（含 nextWatermark 供下次调用）
     */
    @GetMapping("/{layer}/resources/{id}/rows")
    public ApiResponse<DataLayerRowsVO> getResourceRows(@PathVariable String layer,
                                                        @PathVariable String id,
                                                        @RequestParam(required = false) String watermark,
                                                        @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(dataLayerInstanceService.readRows(layer, id, watermark, limit));
    }

    /**
     * 抽样读取分层资源实例行（PMO-B3-1 T2，dry-run 预览用）。
     *
     * @param layer 分层名（须为合法 DataLayer 枚举）
     * @param id    数据资源 ID
     * @param limit 抽样行数上限（空 = 默认 100，硬上限 1000）
     * @return 抽样结果
     */
    @GetMapping("/{layer}/resources/{id}/sample")
    public ApiResponse<DataLayerRowsVO> sampleResourceRows(@PathVariable String layer,
                                                           @PathVariable String id,
                                                           @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(dataLayerInstanceService.sampleRows(layer, id, limit));
    }
}
