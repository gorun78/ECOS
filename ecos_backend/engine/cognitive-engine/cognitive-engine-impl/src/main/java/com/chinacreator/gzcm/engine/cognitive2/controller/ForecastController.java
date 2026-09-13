package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.model.ForecastRequest;
import com.chinacreator.gzcm.engine.cognitive2.model.ForecastResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.engine.cognitive2.service.ModelRegistryService;

import java.util.List;
import java.util.Map;

/**
 * 预测 + 模型注册 REST API — 认知引擎"预测"支柱（PMO-51 T3）。
 *
 * <pre>
 * POST /api/v1/cognitive/forecast         — 时序预测
 * GET  /api/v1/cognitive/models           — 模型注册表列表
 * GET  /api/v1/cognitive/models/{modelId} — 模型详情
 * POST /api/v1/cognitive/models           — 注册模型（新增版本）
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/cognitive")
public class ForecastController {

    private static final Logger log = LoggerFactory.getLogger(ForecastController.class);

    private final ForecastService forecastService;
    private final ModelRegistryService modelRegistryService;

    public ForecastController(ForecastService forecastService,
                               ModelRegistryService modelRegistryService) {
        this.forecastService = forecastService;
        this.modelRegistryService = modelRegistryService;
    }

    /** 时序预测：历史序列 → 未来 horizon 点 + 置信区间。 */
    @PostMapping("/forecast")
    public ApiResponse<ForecastResult> forecast(@RequestBody ForecastRequest request) {
        log.info("预测请求: metric={}, horizon={}, seriesSize={}",
                request.getMetric(), request.getHorizon(),
                request.getSeries() == null ? 0 : request.getSeries().size());
        return ApiResponse.success(forecastService.forecast(request));
    }

    /** 模型注册表列表（按 type 过滤可选）。 */
    @GetMapping("/models")
    public ApiResponse<List<Map<String, Object>>> listModels(@RequestParam(required = false) String modelType) {
        return ApiResponse.success(modelRegistryService.listModels(modelType));
    }

    /** 模型详情。 */
    @GetMapping("/models/{modelId}")
    public ApiResponse<List<Map<String, Object>>> getModel(@PathVariable String modelId) {
        List<Map<String, Object>> rows = modelRegistryService.getModel(modelId);
        if (rows.isEmpty()) {
            return ApiResponse.notFound("认知模型不存在: " + modelId);
        }
        return ApiResponse.success(rows);
    }

    /** 注册模型（同 modelId 新增版本）。 */
    @PostMapping("/models")
    public ApiResponse<Map<String, Object>> registerModel(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(modelRegistryService.registerModel(body));
    }
}
