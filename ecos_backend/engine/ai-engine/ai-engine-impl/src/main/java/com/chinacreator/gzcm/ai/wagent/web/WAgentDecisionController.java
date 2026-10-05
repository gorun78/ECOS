package com.chinacreator.gzcm.ai.wagent.web;

import com.chinacreator.gzcm.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.ai.wagent.WAgentOrchestratorFacade;
import com.chinacreator.gzcm.common.base.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

/**
 * 分册10 §5.2 #21/#22/#23 · Decision / Evidence 编排 Controller。
 * <p>#21 claims（numeric_value {@link BigDecimal} + evidence_grade）；
 * #22 引擎端追溯 endpoint_map（engine→path JSON 字符串）；
 * #23 决策登记 201。LLM 叙事数字由 {@code NarrativeGuard} 剥离（未来接线），
 * 本层只组装结构。无 double。</p>
 */
@RestController
public class WAgentDecisionController {

    private static final Logger log = LoggerFactory.getLogger(WAgentDecisionController.class);

    private final WAgentOrchestratorFacade facade;

    public WAgentDecisionController(WAgentOrchestratorFacade facade) {
        this.facade = facade;
    }

    // #21 GET /claims/{runId} → 200（claims + numeric_value BigDecimal + evidence_grade）
    @Operation(operationId = "listClaims")
    @GetMapping(WAgentApiPaths.LIST_CLAIMS)
    @ResponseStatus(OK)
    public ApiResponse<Map<String, Object>> listClaims(@PathVariable("runId") String runId) {
        log.debug("wagent listClaims runId={}", runId);
        log.debug("wagent listClaims routing=GET {}", WAgentApiPaths.LIST_CLAIMS);
        log.debug("wagent listClaims delegate -> WAgentOrchestratorFacade.listClaims");
        Map<String, Object> body = facade.listClaims(runId);
        log.debug("wagent listClaims result count={}", body.get("count"));
        return ApiResponse.success(body);
    }

    // #22 GET /evidence/{evidenceId} → 200（引擎端追溯 endpoint_map）
    @Operation(operationId = "traceEvidence")
    @GetMapping(WAgentApiPaths.TRACE_EVIDENCE)
    public ApiResponse<Map<String, Object>> traceEvidence(@PathVariable("evidenceId") String evidenceId) {
        log.debug("wagent traceEvidence evidenceId={}", evidenceId);
        log.debug("wagent traceEvidence routing=GET {}", WAgentApiPaths.TRACE_EVIDENCE);
        log.debug("wagent traceEvidence delegate -> WAgentOrchestratorFacade.traceEvidence");
        Map<String, Object> body = facade.traceEvidence(evidenceId);
        log.debug("wagent traceEvidence result endpoint_map={}", body.get("endpoint_map"));
        return ApiResponse.success(body);
    }

    // #23 POST /decisions → 201
    @Operation(operationId = "recordDecision")
    @PostMapping(WAgentApiPaths.RECORD_DECISION)
    @ResponseStatus(CREATED)
    public ApiResponse<Map<String, Object>> recordDecision(@RequestBody RecordDecisionDto dto) {
        log.debug("wagent recordDecision runId={} decision.len={}", dto.runId(),
                dto.decision() == null ? 0 : dto.decision().length());
        log.debug("wagent recordDecision routing=POST {}", WAgentApiPaths.RECORD_DECISION);
        log.debug("wagent recordDecision delegate -> WAgentOrchestratorFacade.recordDecision");
        Map<String, Object> body = facade.recordDecision(dto.runId(), dto.decision(), dto.rationale());
        log.debug("wagent recordDecision result decisionId={}", body.get("decisionId"));
        return ApiResponse.success(body);
    }

    // ── 内联 DTO ──
    public record RecordDecisionDto(String runId, String decision, String rationale,
                                     BigDecimal numericValue, String evidenceGrade,
                                     List<String> claimRefs) {}
}
