package com.chinacreator.gzcm.ai.wagent.web;
import com.chinacreator.gzcm.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.CandidateStatus;
import com.chinacreator.gzcm.ai.wagent.candidate.*;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.springframework.http.HttpStatus.OK;

/**
 * 分册10 §5.2 #17~#20 · Candidate 编排（R-59① 仅协调）。
 * #19 <b>批量接口故意不建</b>（F10-20 语义/口径/知识禁批量，见 {@code BulkApproveGuard}）；
 * #20 publish 必带 approvalToken（无则 400），走 {@link CommitService#publish}。无 double。
 */
@RestController
public class WAgentCandidateController {
    private static final Logger log = LoggerFactory.getLogger(WAgentCandidateController.class);
    private final CandidateService candidates;
    private final CommitService commit;

    public WAgentCandidateController(CandidateService c, CommitService cs) { this.candidates = c; this.commit = cs; }

    // #17 GET /candidates → 200
    @Operation(operationId = "listCandidates")
    @GetMapping(WAgentApiPaths.LIST_CANDIDATES)
    @ResponseStatus(OK)
    public ApiResponse<Map<String, Object>> listCandidates(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "batchId", required = false) String batchId) {
        log.debug("wagent listCandidates type={} status={} batchId={}", type, status, batchId);
        log.debug("wagent listCandidates routing=GET {}", WAgentApiPaths.LIST_CANDIDATES);
        log.debug("wagent listCandidates delegate -> CandidateService.listByBatch");
        List<CandidateModel> rows = (batchId == null) ? List.of() : candidates.listByBatch(batchId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidates", toRows(rows));
        out.put("count", rows.size()); // integer-only
        return ApiResponse.success(out);
    }

    // #18 GET /candidates/{candidateId} → 200（basis + 五段校验）
    @Operation(operationId = "getCandidate")
    @GetMapping(WAgentApiPaths.GET_CANDIDATE)
    public ApiResponse<Map<String, Object>> getCandidate(@PathVariable("candidateId") String id) {
        log.debug("wagent getCandidate id={}", id);
        log.debug("wagent getCandidate routing=GET {}", WAgentApiPaths.GET_CANDIDATE);
        log.debug("wagent getCandidate delegate -> CandidateService.get");
        try {
            CandidateModel c = candidates.get(id);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("candidateId", c.candidateId());    out.put("type", String.valueOf(c.type()));
            out.put("status", String.valueOf(c.status())); out.put("confidence", c.confidence()); // BigDecimal
            out.put("basis", c.basisJson());                      // JSON 语义 String
            out.put("v_structure", c.vStructure());              out.put("v_reference", c.vReference());
            out.put("v_conflict", c.vConflict());                out.put("v_impact", c.vImpact());
            out.put("v_regression", c.vRegression());
            return ApiResponse.success(out);
        } catch (IllegalStateException e) {
            throw new BusinessException(404, "E_NOT_FOUND candidate " + id);
        }
    }

    // #19 POST /candidates/{candidateId}/review → 200（单批；批量故意不建）
    @Operation(operationId = "reviewCandidate")
    @PostMapping(WAgentApiPaths.REVIEW_CANDIDATE)
    @ResponseStatus(OK)
    public ApiResponse<Map<String, Object>> reviewCandidate(
            @PathVariable("candidateId") String id,
            @RequestHeader(value = "X-Reviewer", required = false, defaultValue = "") String revRaw,
            @RequestBody ReviewDto dto) {
        log.debug("wagent reviewCandidate id={} decision={} rev={}", id, dto.decision(), revRaw);
        log.debug("wagent reviewCandidate F10-20: 单笔 only，批量→404 not-found");
        log.debug("wagent reviewCandidate delegate -> CandidateService.approve/updateStatus");
        String rev = (revRaw == null || revRaw.isBlank()) ? "web-reviewer" : revRaw;
        try {
            if ("APPROVE".equalsIgnoreCase(dto.decision())) {
                CandidateModel ap = candidates.approve(id, dto.token(), rev);
                return ApiResponse.success(singleRow(id, String.valueOf(ap.status())));
            }
            candidates.updateStatus(id, CandidateStatus.REJECTED);
            return ApiResponse.success(singleRow(id, "REJECTED"));
        } catch (IllegalStateException e) {
            boolean fourEyes = e.getMessage() != null && e.getMessage().contains("four-eyes");
            throw fourEyes ? new BusinessException(409, "E-WA-STATE " + e.getMessage())
                           : new BusinessException(404, "E_NOT_FOUND candidate " + id);
        }
    }
    // #20 POST /candidates/{candidateId}/publish → 200（必带 approvalToken）
    @Operation(operationId = "publishCandidate")
    @PostMapping(WAgentApiPaths.PUBLISH_CANDIDATE)
    @ResponseStatus(OK)
    public ApiResponse<Map<String, Object>> publishCandidate(
            @PathVariable("candidateId") String id,
            @RequestHeader(value = "X-User", required = false, defaultValue = "") String actor,
            @RequestBody PublishDto dto) {
        log.debug("wagent publishCandidate id={} token.len={}", id,
                dto.approvalToken() == null ? 0 : dto.approvalToken().length());
        log.debug("wagent publishCandidate routing=POST {}", WAgentApiPaths.PUBLISH_CANDIDATE);
        log.debug("wagent publishCandidate delegate -> CommitService.publish");
        if (dto.approvalToken() == null || dto.approvalToken().isBlank())
            throw new BusinessException(400, "E-WA-V: approvalToken 必需");
        String a = (actor == null || actor.isBlank()) ? "wagent-web" : actor;
        CandidateModel p = commit.publish(new CommitService.PublishRiser(id, dto.approvalToken(), a));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidateId", p.candidateId());     out.put("status", String.valueOf(p.status()));
        out.put("publishedRef", p.publishedRef());   out.put("publishedGitRef", p.publishedGitRef());
        return ApiResponse.success(out);
    }
    private static Map<String, Object> singleRow(String id, String status) {
        Map<String, Object> m = new LinkedHashMap<>(); m.put("candidateId", id); m.put("status", status); return m;
    }
    private static List<Map<String, Object>> toRows(List<CandidateModel> rows) {
        return rows.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("candidateId", c.candidateId()); m.put("type", String.valueOf(c.type()));
            m.put("status", String.valueOf(c.status())); m.put("confidence", c.confidence());
            return m;
        }).toList();
    }
    public record ReviewDto(String decision, String token) {
        public ReviewDto { if (decision == null || decision.isBlank()) throw new BusinessException(400, "E-WA-V: decision 必需"); }
    }
    public record PublishDto(String approvalToken) {}
}