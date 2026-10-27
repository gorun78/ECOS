package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive2.ForecastService;
import com.chinacreator.gzcm.engine.cognitive2.model.ForecastRequest;
import com.chinacreator.gzcm.engine.cognitive2.model.ForecastResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.engine.cognitive2.service.ModelRegistryService;

import java.util.List;
import java.util.Map;

/**
 * 预测 + 模型注册 REST API — 认知引擎"预测"支柱（PMO-51 T3；REQ-COG-07 注册中心扩展，本批 #86）。
 *
 * <pre>
 * POST /api/v1/cognitive/forecast              — 时序预测
 * GET  /api/v1/cognitive/models                — 模型注册表列表（?modelType=&registryState=&cognitiveModelType= 均可选）
 * GET  /api/v1/cognitive/models/{modelId}      — 模型详情
 * POST /api/v1/cognitive/models                — 注册模型（新增版本）
 * —— 以下 REQ-COG-07 注册中心新端点族（只增不改；{id} = 版本行 id）——
 * POST /api/v1/cognitive/models/registry          — 注册中心注册（cognitive_model_type 未映射/未知枚举 ⇒ 400 fail-loud）
 * POST /api/v1/cognitive/models/{id}/content-hash  — 计算并冻结规范化内容哈希（DRAFT 面；已发布 ⇒ 409）
 * POST /api/v1/cognitive/models/{id}/gates         — 真实裁决四类发布门禁并落 PASS/FAIL/PENDING（DRAFT 面）
 * POST /api/v1/cognitive/models/{id}/publish       — DRAFT→PUBLISHED 升格（哈希已冻结 ∧ 四门禁全 PASS，否则 422/409）
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

    /**
     * 模型注册表列表（按 type 过滤可选；#146b-C additive 扩参）。
     *
     * <p><b>只增不改</b>：端点路径与既有 {@code modelType} 参数行为原样保留；新增两个可选查询参数
     * 不传时行为与既有端点字节等价：</p>
     * <ul>
     *   <li>{@code registryState}（DRAFT/PUBLISHED，单源 {@code RegistryState}；非契约值 ⇒ 400，
     *       与 registerModel 同 CORM-400 形态）；</li>
     *   <li>{@code cognitiveModelType}（CM-01~CM-07，单源 {@code CognitiveModelType}；未知字面量 ⇒ 400）。</li>
     * </ul>
     * 消费方 = WAgent Readiness：{@code GET /models?registryState=PUBLISHED&cognitiveModelType=CM-03}
     * 命中行 ⇒ {@code cog.cm_ready}；响应行键 {@code backtestGate}（gate_backtest 原值透传）
     * 为 PASS ⇒ {@code cog.backtest_ready}。
     */
    @GetMapping("/models")
    public ApiResponse<List<Map<String, Object>>> listModels(
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String registryState,
            @RequestParam(required = false) String cognitiveModelType) {
        if (registryState == null && cognitiveModelType == null) {
            return ApiResponse.success(modelRegistryService.listModels(modelType));
        }
        return ApiResponse.success(
                modelRegistryService.listModels(modelType, registryState, cognitiveModelType));
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

    // ── REQ-COG-07 注册中心新端点族（additive；路径常量单源 CognitiveApiPaths） ──

    /** 注册中心注册：类型不可解析为 CM-01~07 冻结枚举 ⇒ 400 fail-loud（禁静默默认值）。 */
    @PostMapping(CognitiveApiPaths.MODELS_REGISTRY_REL)
    public ApiResponse<Map<String, Object>> registerOnRegistry(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(modelRegistryService.registerOnRegistry(body));
    }

    /** 计算/冻结规范化内容哈希（SHA-256 hex）；已 PUBLISHED 行不可变 ⇒ 409。 */
    @PostMapping(CognitiveApiPaths.MODELS_HASH_REL)
    public ApiResponse<Map<String, Object>> freezeContentHash(@PathVariable("id") String id) {
        return ApiResponse.success(modelRegistryService.computeAndFreezeContentHash(id));
    }

    /** 真实裁决四类发布门禁（contract/regression/backtest/calibration）并落列。 */
    @PostMapping(CognitiveApiPaths.MODELS_GATES_REL)
    public ApiResponse<Map<String, Object>> runGates(@PathVariable("id") String id) {
        return ApiResponse.success(modelRegistryService.evaluateAndRecordGates(id));
    }

    /** DRAFT→PUBLISHED 升格：四门禁全 PASS ∧ 哈希已冻结，否则显式 4xx（禁静默成功）。 */
    @PostMapping(CognitiveApiPaths.MODELS_PUBLISH_REL)
    public ApiResponse<Map<String, Object>> publish(@PathVariable("id") String id,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        String publishedBy = body == null || body.get("publishedBy") == null
                ? null : String.valueOf(body.get("publishedBy"));
        return ApiResponse.success(modelRegistryService.publish(id, publishedBy));
    }
}
