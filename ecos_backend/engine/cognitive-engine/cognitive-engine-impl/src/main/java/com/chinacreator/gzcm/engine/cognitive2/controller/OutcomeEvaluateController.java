package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluateRequest;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluateResult;
import com.chinacreator.gzcm.engine.cognitive2.service.outcome.OutcomeEvaluateService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * W→D 结果评估面（《认知域与智能域补充详细设计 v1.0》§9 / SUP-OUT-01、SUP-OUT-02）。
 *
 * <p>路径落在冻结常量 {@code CognitiveApiPaths.OUTCOMES_EVALUATE} =
 * {@code /api/v1/cognitive/outcomes/evaluate}，读回端点同前缀下只增不改。</p>
 *
 * <p>三条语义红线由本层承接（Service 已实现，此处只透传，不做二次判断）：</p>
 * <ul>
 *   <li>§9.4 七情形命中<b>不</b>是错误：仍 200 落库，{@code status=NEEDS_RECONCILE} +
 *       {@code blockers[]} 回显（"评估没做成"是业务结论，禁包装成 4xx/5xx）。</li>
 *   <li>SUP-OUT-01：本端点只产出候选引用，对生产 Belief/Rule/Model 资产表零写入。</li>
 *   <li>DG-9：数值接缝（{@code OutcomeMetricValueReader}）未装配时，指标逐项
 *       {@code applicable=false} + {@code MISSING_VALUE_SEAM} 告警，禁以 0 或伪造值填充。</li>
 * </ul>
 *
 * <p>{@code CognitiveGuardException} 由 {@code CognitiveErrorAdvice}（basePackages 已覆盖本包）
 * 统一翻译为冻结码响应，故本类不写 try/catch。</p>
 */
@RestController
@RequestMapping("/api/v1/cognitive")
public class OutcomeEvaluateController {

    private final OutcomeEvaluateService outcomeEvaluateService;

    public OutcomeEvaluateController(OutcomeEvaluateService outcomeEvaluateService) {
        this.outcomeEvaluateService = outcomeEvaluateService;
    }

    /** §9.3 评估主端点：口径对齐 → 七闸裁决 → 误差/分解 → 诊断 → 候选建议 → 闭环落库。 */
    @PostMapping("/outcomes/evaluate")
    public ApiResponse<OutcomeEvaluateResult> evaluate(@RequestBody OutcomeEvaluateRequest request) {
        return ApiResponse.success(outcomeEvaluateService.evaluate(request));
    }

    /** 按评估 ID 读回（未命中 404 {@code ECOS-COG-404O}，由 Service 抛 guard 异常）。 */
    @GetMapping("/outcomes/{evaluationId}")
    public ApiResponse<OutcomeEvaluateResult> get(@PathVariable String evaluationId) {
        return ApiResponse.success(outcomeEvaluateService.get(evaluationId));
    }

    /** 场景维度列表（{@code status} 可选过滤）；空集为 200 + []，与故障严格可分（DG-6）。 */
    @GetMapping("/outcomes")
    public ApiResponse<List<OutcomeEvaluateResult>> listByScenario(
            @RequestParam String scenarioId,
            @RequestParam(required = false) String status) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_OUTCOME_INVALID,
                    CognitiveErrorCode.MSG_OUTCOME_INVALID, "scenarioId required");
        }
        return ApiResponse.success(outcomeEvaluateService.listByScenario(scenarioId, status));
    }

    /** 运行维度列表（一次 run 可能对应多次评估）。 */
    @GetMapping("/outcomes/by-run/{runId}")
    public ApiResponse<List<OutcomeEvaluateResult>> listByRun(@PathVariable String runId) {
        if (runId == null || runId.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_OUTCOME_INVALID,
                    CognitiveErrorCode.MSG_OUTCOME_INVALID, "runId required");
        }
        return ApiResponse.success(outcomeEvaluateService.listByRun(runId));
    }
}
