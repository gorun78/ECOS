package com.chinacreator.gzcm.ai.wagent.web;

import com.chinacreator.gzcm.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.ai.wagent.WAgentOrchestratorFacade;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

/**
 * 分册10 §5.2 #1/#2 · Goal 编排 Controller（R-59① 仅协调，不做金额运算）。
 * <p>委托 {@link WAgentOrchestratorFacade} 取结构；#2 被引用锁定 → 409 E-WA-STATE。
 * 只 assert 语义；仅构造器注入；无 JdbcTemplate / Scheduler / double。</p>
 */
@RestController
public class WAgentGoalController {

    private static final Logger log = LoggerFactory.getLogger(WAgentGoalController.class);

    private final WAgentOrchestratorFacade facade;

    public WAgentGoalController(WAgentOrchestratorFacade facade) {
        this.facade = facade;
    }

    // #1 POST /goals → 201（integer-only locale-safe）
    @Operation(operationId = "createGoal")
    @PostMapping(WAgentApiPaths.CREATE_GOAL)
    @ResponseStatus(CREATED)
    public ApiResponse<Map<String, Object>> createGoal(@RequestBody CreateGoalDto dto) {
        log.debug("wagent createGoal type={} desc.len={}", dto.goalType(),
                dto.description() == null ? 0 : dto.description().length());
        log.debug("wagent createGoal routing=POST {}", WAgentApiPaths.CREATE_GOAL);
        log.debug("wagent createGoal delegate -> WAgentOrchestratorFacade.createGoal");
        Map<String, Object> body = facade.createGoal(dto.goalId(), dto.goalType(), dto.description());
        log.debug("wagent createGoal result goalId={}", body.get("goalId"));
        return ApiResponse.success(body);
    }

    // #2 PATCH /goals/{goalId} → 200；被引用 → 409 E-WA-STATE
    @Operation(operationId = "reviseGoal")
    @PatchMapping(WAgentApiPaths.REVISE_GOAL)
    @ResponseStatus(OK)
    public ApiResponse<Map<String, Object>> reviseGoal(@PathVariable("goalId") String goalId,
                                                        @RequestBody ReviseGoalDto dto) {
        log.debug("wagent reviseGoal goalId={} newType={}", goalId, dto.goalType());
        log.debug("wagent reviseGoal ref-check facade.goalReferenced({})", goalId);
        log.debug("wagent reviseGoal delegate -> WAgentOrchestratorFacade.reviseGoal");
        if (facade.goalReferenced(goalId)) {
            log.debug("wagent reviseGoal goalID={} locked -> 409 E-WA-STATE", goalId);
            throw new BusinessException(409, "E-WA-STATE goal locked");
        }
        Map<String, Object> body = facade.reviseGoal(goalId, dto.goalType());
        log.debug("wagent reviseGoal result version={}", body.get("version"));
        return ApiResponse.success(body);
    }

    // ── 内联 DTO（record；无 double/float）──
    public record CreateGoalDto(String goalId, String goalType, String description) {
        public CreateGoalDto {
            Objects.requireNonNull(goalType, "goalType");
        }
    }

    public record ReviseGoalDto(String goalType) {
        public ReviseGoalDto {
            Objects.requireNonNull(goalType, "goalType");
        }
    }
}
