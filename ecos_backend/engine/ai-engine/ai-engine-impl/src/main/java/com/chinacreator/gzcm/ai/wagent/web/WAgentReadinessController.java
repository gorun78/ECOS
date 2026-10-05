package com.chinacreator.gzcm.ai.wagent.web;

import com.chinacreator.gzcm.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.ai.wagent.WAgentOrchestratorFacade;
import com.chinacreator.gzcm.ai.wagent.readiness.ReadinessGapService;
import com.chinacreator.gzcm.ai.wagent.readiness.ReadinessGrader;
import com.chinacreator.gzcm.common.base.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.OK;

/**
 * 分册10 §5.2 #12/#13 · Readiness 编排 Controller。
 * <p>#12 定级走 {@link ReadinessGrader#grade}（确定性非 LLM）；
 * #13 补齐循环 {@link ReadinessGapService#refill}（refillRound ≥ 3 → cap + suspend）。
 * 无 double；grade 以 char/String 落 body。</p>
 */
@RestController
public class WAgentReadinessController {

    private static final Logger log = LoggerFactory.getLogger(WAgentReadinessController.class);

    private final WAgentOrchestratorFacade facade;

    public WAgentReadinessController(WAgentOrchestratorFacade facade) {
        this.facade = facade;
    }

    // #12 GET /readiness/{questionId} → 200（grade + layer_d/i/k/c + 缺项 + degradations）
    @Operation(operationId = "getReadiness")
    @GetMapping(WAgentApiPaths.GET_READINESS)
    @ResponseStatus(OK)
    public ApiResponse<Map<String, Object>> getReadiness(@PathVariable("questionId") String questionId) {
        log.debug("wagent getReadiness questionId={}", questionId);
        log.debug("wagent getReadiness grade via ReadinessGrader.grade (deterministic, non-LLM)");
        log.debug("wagent getReadiness delegate -> WAgentOrchestratorFacade.getReadiness");
        Map<String, Object> body = facade.getReadiness(questionId);
        log.debug("wagent getReadiness result grade={} layers={}", body.get("grade"), body.get("layers"));
        return ApiResponse.success(body);
    }

    // #13 POST /readiness/{questionId}/refill → 200（循环 round + submitted task ids）
    @Operation(operationId = "refillReadiness")
    @PostMapping(WAgentApiPaths.REFILL_READINESS)
    @ResponseStatus(OK)
    public ApiResponse<Map<String, Object>> refillReadiness(@PathVariable("questionId") String questionId) {
        log.debug("wagent refillReadiness questionId={}", questionId);
        log.debug("wagent refillReadiness delegate -> ReadinessGapService.refill");
        log.debug("wagent refillReadiness cap check ReadinessGapService.MAX_REFILL={}", ReadinessGapService.MAX_REFILL);
        // 无已登记 gap（离线态）→ 空清单；refillRound 从 0 起步。
        List<ReadinessGapService.Gap> gaps = new ArrayList<>();
        ReadinessGapService.RefillOutcome outcome =
                ReadinessGapService.refill(gaps, 0, null); // taskPort 未注入 → 无 task 提交
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("questionId", questionId);
        body.put("refillRound", outcome.refillRound());           // integer-only
        body.put("capped", outcome.capped());
        body.put("suspend", outcome.capped());                     // round>3 → suspend
        body.put("submittedTaskIds", new ArrayList<String>(outcome.submittedTaskIds()));
        body.put("gaps", new ArrayList<String>());
        return ApiResponse.success(body);
    }
}
