package com.chinacreator.gzcm.engine.cognitive2.controller.scenario;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive.api.observability.CognitiveEventLog;
import com.chinacreator.gzcm.engine.cognitive.api.observability.CognitiveEventLog.Op;
import com.chinacreator.gzcm.engine.cognitive2.observability.CognitiveObservability;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.ScenarioCognitiveQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场景认知四端点（设计-05 F05-03~07 / D.1 / C.1；R-14 ① 定版属主 = cognitive；对外路径冻结不变，
 * workspace 场景层只编排不生产 铁律 §0.6）。
 *
 * <p>四端点全 GET 只读、Zero DB-Write（B-2）；错误矩阵 6 行冻结字面量，经 {@link CognitiveErrorAdvice}
 * 拦下；前置门：CognitiveAvailabilityGuard（enabled/schema）与 ScenarioGuard（存在/软删）与
 * MindScopeGuard（mind-scenario 属主）分别上抛。
 *
 * <p>Tracker @RequestMapping: {@code /api/v1/business/scenarios/{scenarioId}/cognition}（走
 * {@link CognitiveApiPaths#SCENARIO_COGNITION_PREFIX_TEMPLATE} 常量拼装，禁字面量路径）。</p>
 */
@RestController
public class ScenarioCognitionController {

    private final ScenarioCognitiveQueryService queryService;
    private final CognitiveObservability observability;

    public ScenarioCognitionController(ScenarioCognitiveQueryService queryService,
                                       CognitiveObservability observability) {
        this.queryService = queryService;
        this.observability = observability;
    }

    /** E1 — 场景可用 Mind 探测（GET，无 mind 参数；空场景 = 200 data:[] DG-6）。 */
    @GetMapping(value = CognitiveApiPaths.SCENARIO_COGNITION_URI_TEMPLATE + CognitiveApiPaths.SCENARIO_COGNITION_DETECT)
    public ApiResponse<List<CognitiveContractSchema.MindVO>> detect(@PathVariable String scenarioId) {
        long start = observability.start();
        try {
            queryService.assertEnabled();
            queryService.assertScenario(scenarioId);
            List<CognitiveContractSchema.MindVO> out = queryService.detect(scenarioId);
            observability.event(Op.DETECT, null, scenarioId, null, start,
                    CognitiveEventLog.ofSize(out.size()));
            return ApiResponse.success(out);
        } catch (CognitiveGuardException e) {
            observability.eventGuard(Op.DETECT, null, scenarioId, null, start, e);
            throw e;
        }
    }

    /** E2 — 运行评价（GET；mind 必带；limit 默认 10 上限 50；{@code trend=FLAT trendSource=NOT_IMPLEMENTED}）。 */
    @GetMapping(value = CognitiveApiPaths.SCENARIO_COGNITION_URI_TEMPLATE + CognitiveApiPaths.SCENARIO_COGNITION_OPERATION_EVAL)
    public ApiResponse<CognitiveContractSchema.OperationEvalData> operationEval(
            @PathVariable String scenarioId,
            @RequestParam(CognitiveApiPaths.PARAM_MIND) String mind,
            @RequestParam(value = CognitiveApiPaths.PARAM_LIMIT, required = false) Integer limit) {
        long start = observability.start();
        try {
            queryService.assertEnabled();
            queryService.assertScenario(scenarioId);
            queryService.assertMindBound(scenarioId, mind);
            if (limit != null && limit < 0) {
                throw new CognitiveGuardException(
                        com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode.E400_INVALID_PARAM,
                        com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode.MSG_INVALID_PARAM);
            }
            CognitiveContractSchema.OperationEvalData data = queryService.operationEval(scenarioId, mind, limit);
            observability.event(Op.EVAL, null, scenarioId, mind, start,
                    CognitiveEventLog.ofSize(data.getMetrics().size()));
            observability.counter(CognitiveEventLog.METRIC_EVAL_TRUNCATED, data.isTruncated() ? 1 : 0);
            return ApiResponse.success(data);
        } catch (CognitiveGuardException e) {
            observability.eventGuard(Op.EVAL, null, scenarioId, mind, start, e);
            throw e;
        }
    }

    /** E3 — 假设只读（GET；mind 可选；无 mind → 场景全部；恒带 is_deleted=0；updatedAt desc）。 */
    @GetMapping(value = CognitiveApiPaths.SCENARIO_COGNITION_URI_TEMPLATE + CognitiveApiPaths.SCENARIO_COGNITION_HYPOTHESES)
    public ApiResponse<List<CognitiveContractSchema.HypothesisVO>> hypotheses(
            @PathVariable String scenarioId,
            @RequestParam(value = CognitiveApiPaths.PARAM_MIND, required = false) String mind) {
        long start = observability.start();
        try {
            queryService.assertEnabled();
            queryService.assertScenario(scenarioId);
            if (mind != null && !mind.isBlank()) queryService.assertMindBound(scenarioId, mind);
            List<CognitiveContractSchema.HypothesisVO> out = queryService.hypotheses(scenarioId, mind);
            observability.event(Op.HYP, null, scenarioId, mind, start, CognitiveEventLog.ofSize(out.size()));
            return ApiResponse.success(out);
        } catch (CognitiveGuardException e) {
            observability.eventGuard(Op.HYP, null, scenarioId, mind, start, e);
            throw e;
        }
    }

    /** E4 — 信念只读（GET；mind 可选；capability=null 不丢行 DG-7）。 */
    @GetMapping(value = CognitiveApiPaths.SCENARIO_COGNITION_URI_TEMPLATE + CognitiveApiPaths.SCENARIO_COGNITION_BELIEFS)
    public ApiResponse<List<CognitiveContractSchema.BeliefVO>> beliefs(
            @PathVariable String scenarioId,
            @RequestParam(value = CognitiveApiPaths.PARAM_MIND, required = false) String mind) {
        long start = observability.start();
        try {
            queryService.assertEnabled();
            queryService.assertScenario(scenarioId);
            if (mind != null && !mind.isBlank()) queryService.assertMindBound(scenarioId, mind);
            List<CognitiveContractSchema.BeliefVO> out = queryService.beliefs(scenarioId, mind);
            observability.event(Op.BELIEF, null, scenarioId, mind, start, CognitiveEventLog.ofSize(out.size()));
            observability.counter(CognitiveEventLog.METRIC_CAPABILITY_UNMAPPED, countUnmapped(out));
            return ApiResponse.success(out);
        } catch (CognitiveGuardException e) {
            observability.eventGuard(Op.BELIEF, null, scenarioId, mind, start, e);
            throw e;
        }
    }

    /** DG-7：capability 未映射行数（不丢行，计数经 runtime-monitor 门面暴露）。 */
    private static int countUnmapped(List<CognitiveContractSchema.BeliefVO> rows) {
        int unmapped = 0;
        for (CognitiveContractSchema.BeliefVO row : rows) {
            if (row.getCapability() == null) unmapped++;
        }
        return unmapped;
    }
}
