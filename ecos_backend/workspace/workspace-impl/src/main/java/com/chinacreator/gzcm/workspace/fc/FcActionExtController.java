package com.chinacreator.gzcm.workspace.fc;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分册09 F09-12 · 经营动作（FC-04 五必填 + 决策底座回链）REST API（§5.1 #17）。
 *
 * <p><b>铁律</b>（R-30 受控组合）：workspace <b>不建第二套决策底座</b>——1:1 挂 cognitive
 * {@code ecos_decision} 底座；本 controller 只做 FC-04 五必填校验 + 路由提示；写入走
 * cognitive>{@code /api/v1/cognitive/decision} 或 sysman 侧决策 facade（生产链路 gateway 反向代理）。
 * 五必填缺一 → 400（C206 应用层红线，护栏可离线单测 {@code FcActionClosureTest#requiresAllFiveMandatoryFields}）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/forecast-runs")
public class FcActionExtController {

    private static final Logger log = LoggerFactory.getLogger(FcActionExtController.class);

    /** FC-04 五必填（V218 {@code ecos_fc_action_ext} 五列 NOT NULL）：action_desc/owner_id/due_date/expected_impact/kpi_text。 */
    public record ActionCreateDto(String actionDesc, String ownerId, LocalDate dueDate,
                                   java.math.BigDecimal expectedImpact, String kpiText,
                                   String decisionId, String forecastRunId,
                                   String factorRef, String projectId) {
        /** 五必填完整校验（C206）——缺一即拒。外部调用：offline test + Preview 都可复用。 */
        public List<String> missing() {
            java.util.ArrayList<String> missing = new java.util.ArrayList<>();
            if (actionDesc == null || actionDesc.isBlank()) missing.add("action_desc");
            if (ownerId == null || ownerId.isBlank()) missing.add("owner_id");
            if (dueDate == null) missing.add("due_date");
            if (expectedImpact == null) missing.add("expected_impact");
            if (kpiText == null || kpiText.isBlank()) missing.add("kpi_text");
            return missing;
        }
    }

    /** §5.1 #17 createFcAction：5 必填缺一 400；预填 runId/factorRef/projectId；body 落 cognitive 底座 + ext 扩展。 */
    @Operation(operationId = "createFcAction", summary = "createFcAction（五必填缺一 400；写侧 delegate → cognitive 决策底座 + ext 列扩展）")
    @PostMapping("/{runId}/actions")
    public ApiResponse<Map<String, Object>> createAction(@PathVariable String runId, @RequestBody ActionCreateDto dto) {
        List<String> missing = dto.missing();
        if (!missing.isEmpty()) {
            throw new BusinessException(400, "FC-04 required 5 missing: " + missing);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", runId);
        m.put("decisionId", dto.decisionId());
        m.put("forecastRunId", runId);
        m.put("factorRef", dto.factorRef());
        m.put("projectId", dto.projectId());
        m.put("status", "DRAFT");
        m.put("routingTo", "POST /api/v1/cognitive/decision " // decision 底座
            + "→ then workspace ext row (V218 public.ecos_fc_action_ext) via data-engine write-channel");
        m.put("auditEvents", "Kafka ecos.audit: action_created / action_approved / action_closed (ST06)");
        log.info("createFcAction runId={} actionDescLen={} (write-side delegated)", runId,
                dto.actionDesc() == null ? 0 : dto.actionDesc().length());
        return ApiResponse.success(m);
    }
}
