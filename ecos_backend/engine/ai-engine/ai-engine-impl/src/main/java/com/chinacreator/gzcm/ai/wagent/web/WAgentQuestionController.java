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
import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

/**
 * 分册10 §5.2 #3/#4 · Question 编排 Controller。
 * <p>#3 返回 intent + confidence({@link BigDecimal}) + slots_source(JSON 字符串)；
 * confidence &lt; 0.70 保守返回 {@code status=CLARIFY}（F10-05 不 fail-open 建 Run）。
 * 只委托 facade，无金额运算、无 double。</p>
 */
@RestController
public class WAgentQuestionController {

    private static final Logger log = LoggerFactory.getLogger(WAgentQuestionController.class);

    private final WAgentOrchestratorFacade facade;

    public WAgentQuestionController(WAgentOrchestratorFacade facade) {
        this.facade = facade;
    }

    // #3 POST /questions → 201
    @Operation(operationId = "createQuestion")
    @PostMapping(WAgentApiPaths.CREATE_QUESTION)
    @ResponseStatus(CREATED)
    public ApiResponse<Map<String, Object>> createQuestion(@RequestBody CreateQuestionDto dto) {
        log.debug("wagent createQuestion intent={} conf={}", dto.intent(), dto.confidence());
        log.debug("wagent createQuestion slots_source.len={}",
                dto.slotsSourceJson() == null ? 0 : dto.slotsSourceJson().length());
        log.debug("wagent createQuestion delegate -> WAgentOrchestratorFacade.createQuestion");
        Map<String, Object> body = facade.createQuestion(dto.questionId(), dto.intent(),
                dto.confidence(), dto.slotsSourceJson());
        log.debug("wagent createQuestion result questionId={} status={}", body.get("questionId"), body.get("status"));
        return ApiResponse.success(body);
    }

    // #4 GET /questions/{questionId} → 200（含 latest_run 摘要字符串）
    @Operation(operationId = "getQuestion")
    @GetMapping(WAgentApiPaths.GET_QUESTION)
    @ResponseStatus(OK)
    public ApiResponse<Map<String, Object>> getQuestion(@PathVariable("questionId") String questionId) {
        log.debug("wagent getQuestion questionId={}", questionId);
        log.debug("wagent getQuestion routing=GET {}", WAgentApiPaths.GET_QUESTION);
        log.debug("wagent getQuestion delegate -> WAgentOrchestratorFacade.getQuestion");
        Map<String, Object> body = facade.getQuestion(questionId);
        log.debug("wagent getQuestion result latest_run={}", body.get("latest_run"));
        return ApiResponse.success(body);
    }

    // ── 内联 DTO ──
    public record CreateQuestionDto(String questionId, String intent, BigDecimal confidence,
                                     String slotsSourceJson) {
        public CreateQuestionDto {
            Objects.requireNonNull(intent, "intent");
            confidence = confidence == null ? BigDecimal.ZERO : confidence;
        }
    }
}
