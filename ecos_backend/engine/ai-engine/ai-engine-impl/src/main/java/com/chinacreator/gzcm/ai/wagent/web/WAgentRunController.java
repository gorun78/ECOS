package com.chinacreator.gzcm.ai.wagent.web;

import com.chinacreator.gzcm.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.ai.wagent.WAgentOrchestratorFacade;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

/**
 * 分册10 §5.2 #5~#10 · Run 编排 Controller。
 * <p>#5 读 {@code Prefer: respond-async} header 定 201(同步)/202(异步)；
 * #7 SSE 端点骨架：P-4 未闭合时抛 501 降级轮询（保持 TEXT_EVENT_STREAM produces 钩子）；
 * 预算剩余由 {@link WAgentApiPaths} 常量富集后经 facade 返回。无 double。</p>
 */
@RestController
public class WAgentRunController {

    private static final Logger log = LoggerFactory.getLogger(WAgentRunController.class);

    private final WAgentOrchestratorFacade facade;

    public WAgentRunController(WAgentOrchestratorFacade facade) {
        this.facade = facade;
    }

    // #5 POST /runs → 201 同步 / 202 异步（Prefer: respond-async）
    @Operation(operationId = "startRun")
    @PostMapping(WAgentApiPaths.START_RUN)
    public ResponseEntity<ApiResponse<Map<String, Object>>> startRun(
            @RequestHeader(value = "Prefer", required = false) String prefer,
            @RequestBody StartRunDto dto) {
        boolean async = prefer != null && prefer.toLowerCase().contains("respond-async");
        log.debug("wagent startRun prefer={} async={}", prefer, async);
        log.debug("wagent startRun idempotencyKey={}", dto.idempotencyKey());
        log.debug("wagent startRun delegate -> WAgentOrchestratorFacade.startRun");
        Map<String, Object> body = facade.startRun(dto.idempotencyKey(), dto.questionId(), async);
        HttpStatus status = async ? HttpStatus.ACCEPTED : CREATED;
        return ResponseEntity.status(status).body(ApiResponse.success(body));
    }

    // #6 GET /runs/{runId} → 200（status + timeline 事件数组 + 预算剩余）
    @Operation(operationId = "getRun")
    @GetMapping(WAgentApiPaths.GET_RUN)
    public ApiResponse<Map<String, Object>> getRun(@PathVariable("runId") String runId) {
        log.debug("wagent getRun runId={}", runId);
        log.debug("wagent getRun routing=GET {}", WAgentApiPaths.GET_RUN);
        log.debug("wagent getRun delegate -> WAgentOrchestratorFacade.getRun");
        Map<String, Object> body = facade.getRun(runId);
        log.debug("wagent getRun result status={}", body.get("status"));
        return ApiResponse.success(body);
    }

    // #7 GET /runs/{runId}/events → SSE 骨架：P-4 未闭合 → 501 降级轮询
    @Operation(operationId = "streamRunEvents")
    @GetMapping(value = WAgentApiPaths.STREAM_RUN_EVENTS,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void streamRunEvents(@PathVariable("runId") String runId) {
        log.debug("wagent streamRunEvents runId={}", runId);
        log.debug("wagent streamRunEvents P-4 BFF SSE 未闭合（分册08 §4.3）");
        log.debug("wagent streamRunEvents degradedTo=polling, throwing 501");
        throw new BusinessException(501, "SSE not available, use polling (P-4 未闭合)");
    }

    // #8 POST /runs/{runId}/input → 200
    @Operation(operationId = "submitRunInput")
    @PostMapping(WAgentApiPaths.SUBMIT_RUN_INPUT)
    public ApiResponse<Map<String, Object>> submitRunInput(@PathVariable("runId") String runId,
                                                            @RequestBody Map<String, Object> input) {
        log.debug("wagent submitRunInput runId={} fields={}", runId, input == null ? 0 : input.size());
        log.debug("wagent submitRunInput routing=POST {}", WAgentApiPaths.SUBMIT_RUN_INPUT);
        log.debug("wagent submitRunInput delegate -> WAgentOrchestratorFacade.submitRunInput");
        Map<String, Object> body = facade.submitRunInput(runId, input);
        log.debug("wagent submitRunInput result accepted={}", body.get("accepted"));
        return ApiResponse.success(body);
    }

    // #9 POST /runs/{runId}/approval → 200
    @Operation(operationId = "approveRunGate")
    @PostMapping(WAgentApiPaths.APPROVE_RUN_GATE)
    public ApiResponse<Map<String, Object>> approveRunGate(@PathVariable("runId") String runId,
                                                            @RequestBody ApprovalDto dto) {
        log.debug("wagent approveRunGate runId={} token.len={}", runId,
                dto.approvalToken() == null ? 0 : dto.approvalToken().length());
        log.debug("wagent approveRunGate routing=POST {}", WAgentApiPaths.APPROVE_RUN_GATE);
        log.debug("wagent approveRunGate delegate -> WAgentOrchestratorFacade.approveRunGate");
        Map<String, Object> body = facade.approveRunGate(runId, dto.approvalToken());
        log.debug("wagent approveRunGate result status={}", body.get("status"));
        return ApiResponse.success(body);
    }

    // #10 POST /runs/{runId}/cancel → 200 幂等
    @Operation(operationId = "cancelRun")
    @PostMapping(WAgentApiPaths.CANCEL_RUN)
    public ApiResponse<Map<String, Object>> cancelRun(@PathVariable("runId") String runId) {
        log.debug("wagent cancelRun runId={}", runId);
        log.debug("wagent cancelRun routing=POST {}", WAgentApiPaths.CANCEL_RUN);
        log.debug("wagent cancelRun delegate -> WAgentOrchestratorFacade.cancelRun (idempotent)");
        Map<String, Object> body = facade.cancelRun(runId);
        log.debug("wagent cancelRun result status={} idempotent={}", body.get("status"), body.get("idempotent"));
        return ApiResponse.success(body);
    }

    // ── 内联 DTO ──
    public record StartRunDto(String idempotencyKey, String questionId) {}

    public record ApprovalDto(String approvalToken) {
        public ApprovalDto {
            if (approvalToken == null || approvalToken.isBlank()) {
                throw new BusinessException(400, "E-WA-V: approvalToken 非空");
            }
        }
    }
}
