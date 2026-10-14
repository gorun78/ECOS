package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunComputeService;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunComputeService.ComputeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 预测 run 计算与读回 REST API（详设-09 §十一 H-5 认知侧半区 / 实现差距分析 P0-4 断点接通）。
 *
 * <pre>
 * POST /api/v1/cognitive/forecast-runs/compute                        — 取事实→确定性装配→唯一写通道落库→回读
 * GET  /api/v1/cognitive/forecast-runs/{forecastRunId}                 — run 视图（六要素 + 区间三值原样）
 * GET  /api/v1/cognitive/forecast-runs/by-idempotency-key/{key}        — 幂等定位（未命中 data=null，非 404）
 * GET  /api/v1/cognitive/forecast-runs/{forecastRunId}/results         — 结果明细（缺值 null，禁插值）
 * GET  /api/v1/cognitive/forecast-runs/{forecastRunId}/evidence        — 证据引用集
 * </pre>
 *
 * <p><b>为什么端点族属主是 cognitive 而不是 workspace</b>：workspace 场景层「只编排不生产」
 * （R-14 ①），run 的计算与落库属认知域确定性计算，落库经 data-engine 唯一写通道（ST08）。
 * 本 Controller 是 workspace {@code ForecastRunGateway} SPI 的<b>被调方</b>，
 * 编排面经 REST 适配器接入（不直连 {@code ecos_dw}、不注入 cognitive bean ——
 * gateway 扫描面 {@code ComponentScanNarrowingTest} 冻结，见卷07 F07-16 分层纪律）。</p>
 *
 * <p><b>失败语义诚实</b>（DG-9 / X-44）：本层不吞异常、不造 200 —— 缺参 400、
 * run 不存在 404 {@code ECOS-COG-404F}、读通道不可达 502→503、依赖未上线 501，
 * 统一由 {@code CognitiveErrorAdvice} 按冻结码出参；区间三值物理无列 ⇒ 原样 null（R-62 ①）。</p>
 *
 * <p>路径单源：全部映射值引用 {@link CognitiveApiPaths} 常量
 * （{@code CognitiveContractSingleSourceTest#noInlineCognitivePathLiterals} 门禁）。</p>
 */
@RestController
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class ForecastRunComputeController {

    private static final Logger log = LoggerFactory.getLogger(ForecastRunComputeController.class);

    private final ForecastRunComputeService computeService;

    public ForecastRunComputeController(ForecastRunComputeService computeService) {
        this.computeService = computeService;
    }

    /** 计算并落库一个预测 run（编排面给定 run 身份 + 六要素，禁调用方自造幂等键/指纹）。 */
    @PostMapping(CognitiveApiPaths.FORECAST_RUNS_COMPUTE_REL)
    public ApiResponse<Map<String, Object>> compute(@RequestBody ComputeRequest request) {
        log.info("预测 run 计算请求 runId={} scenario={} caliber={}@{} projects={} periods={}",
                request == null ? null : request.forecastRunId(),
                request == null ? null : request.scenarioId(),
                request == null ? null : request.caliberId(),
                request == null ? null : request.caliberVersion(),
                request == null || request.projectIds() == null ? 0 : request.projectIds().size(),
                request == null || request.periods() == null ? 0 : request.periods().size());
        return ApiResponse.success(computeService.compute(request));
    }

    /** run 视图（无行由服务层抛 404 {@code ECOS-COG-404F}，禁伪装空视图）。 */
    @GetMapping(CognitiveApiPaths.FORECAST_RUNS_ITEM_REL)
    public ApiResponse<Map<String, Object>> getRun(@PathVariable(CognitiveApiPaths.PARAM_FORECAST_RUN_ID)
                                                   String forecastRunId) {
        return ApiResponse.success(computeService.findRun(forecastRunId));
    }

    /**
     * 幂等键定位：<b>未命中返回 {@code data=null}（code=0）</b>。
     *
     * <p>这是"首次运行"的正常判定分支而非资源缺失，故与 {@link #getRun} 的 404 严格分开；
     * 编排面据 null 继续提交，据非空 {@code contentHash} 判复用/冲突。</p>
     */
    @GetMapping(CognitiveApiPaths.FORECAST_RUNS_BY_KEY_REL)
    public ApiResponse<Map<String, Object>> getByIdempotencyKey(
            @PathVariable(CognitiveApiPaths.PARAM_IDEMPOTENCY_KEY) String idempotencyKey) {
        return ApiResponse.success(computeService.findByIdempotencyKey(idempotencyKey));
    }

    /** 结果明细（区间三值缺位 null，编排面不插值）。 */
    @GetMapping(CognitiveApiPaths.FORECAST_RUNS_RESULTS_REL)
    public ApiResponse<Map<String, Object>> results(@PathVariable(CognitiveApiPaths.PARAM_FORECAST_RUN_ID)
                                                    String forecastRunId) {
        return ApiResponse.success(computeService.readResults(forecastRunId));
    }

    /** 证据引用集（后端组装：六要素锚 + 因果边 evidenceRefs）。 */
    @GetMapping(CognitiveApiPaths.FORECAST_RUNS_EVIDENCE_REL)
    public ApiResponse<Map<String, Object>> evidence(@PathVariable(CognitiveApiPaths.PARAM_FORECAST_RUN_ID)
                                                     String forecastRunId) {
        return ApiResponse.success(computeService.readEvidence(forecastRunId));
    }
}
