package com.chinacreator.gzcm.ai.wagent.web;

import com.chinacreator.gzcm.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.ai.wagent.WAgentOrchestratorFacade;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

/**
 * 分册10 §5.2 #24a/#24b · Action 编排 Controller。
 * <p>#24a draftAction：7 字段中 5 必填（optionId/title/ownerId/role/dueDate）缺一 → 400（C206 类同）；
 * #24b commitAction：已 published → 409 E-WA-STATE。日期以 String 落 body（禁 double）。
 * 只协调，不做金额运算。</p>
 */
@RestController
public class WAgentActionController {

    private static final Logger log = LoggerFactory.getLogger(WAgentActionController.class);
    /** #24a 5 必填字段名单源（C206 类同：缺任一即 400）。 */
    private static final List<String> REQUIRED_FIELDS =
            List.of("optionId", "title", "ownerId", "role", "dueDate");

    private final WAgentOrchestratorFacade facade;

    public WAgentActionController(WAgentOrchestratorFacade facade) {
        this.facade = facade;
    }

    // #24a POST /actions → 201（5 必填缺一 → 400）
    @Operation(operationId = "draftAction")
    @PostMapping(WAgentApiPaths.DRAFT_ACTION)
    @ResponseStatus(CREATED)
    public ApiResponse<Map<String, Object>> draftAction(@RequestBody DraftActionDto dto) {
        log.debug("wagent draftAction optionId={} title={}", dto.optionId(), dto.title());
        log.debug("wagent draftAction C206 类同 5 必填校验 optionId/title/ownerId/role/dueDate");
        log.debug("wagent draftAction delegate -> WAgentOrchestratorFacade.draftAction");
        String missing = firstMissing(dto);
        if (missing != null) {
            log.debug("wagent draftAction missing={} -> 400", missing);
            throw new BusinessException(400, "E-WA-V: draftAction 缺必填字段 " + missing);
        }
        Map<String, Object> body = facade.draftAction(dto.optionId(), dto.title(), dto.ownerId(),
                dto.role(), dto.dueDate(), dto.kpiText(), dto.sourceType());
        log.debug("wagent draftAction result actionId={}", body.get("actionId"));
        return ApiResponse.success(body);
    }

    // #24b POST /actions/{id}/commit → 200（已 published → 409 E-WA-STATE）
    @Operation(operationId = "commitAction")
    @PostMapping(WAgentApiPaths.COMMIT_ACTION)
    @ResponseStatus(OK)
    public ApiResponse<Map<String, Object>> commitAction(@PathVariable("id") String id,
                                                          @RequestBody CommitDto dto) {
        log.debug("wagent commitAction id={} token.len={}", id,
                dto.approvalToken() == null ? 0 : dto.approvalToken().length());
        log.debug("wagent commitAction routing=POST {}", WAgentApiPaths.COMMIT_ACTION);
        log.debug("wagent commitAction delegate -> WAgentOrchestratorFacade.commitAction");
        if (facade.actionPublished(id)) {
            log.debug("wagent commitAction id={} 已 published -> 409 E-WA-STATE", id);
            throw new BusinessException(409, "E-WA-STATE action already published");
        }
        if (dto.approvalToken() == null || dto.approvalToken().isBlank()) {
            log.debug("wagent commitAction token 缺失 -> 400");
            throw new BusinessException(400, "E-WA-V: approvalToken 必需");
        }
        Map<String, Object> body = facade.commitAction(id, dto.approvalToken());
        log.debug("wagent commitAction result status={}", body.get("status"));
        return ApiResponse.success(body);
    }

    /** C206 类同：逐字段判定，返回首个缺失字段名（全齐 → null）。 */
    private static String firstMissing(DraftActionDto dto) {
        if (dto.optionId() == null || dto.optionId().isBlank()) return "optionId";
        if (dto.title() == null || dto.title().isBlank()) return "title";
        if (dto.ownerId() == null || dto.ownerId().isBlank()) return "ownerId";
        if (dto.role() == null || dto.role().isBlank()) return "role";
        if (dto.dueDate() == null || dto.dueDate().isBlank()) return "dueDate";
        return null;
    }

    // ── 内联 DTO ──
    public record DraftActionDto(String optionId, String title, String ownerId, String role,
                                  String dueDate, String kpiText, String sourceType) {}

    public record CommitDto(String approvalToken) {}
}
