package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.decision.AssetRef;
import com.chinacreator.gzcm.engine.security.service.decision.DecisionSubject;
import com.chinacreator.gzcm.engine.security.service.decision.Purpose;
import com.chinacreator.gzcm.engine.security.service.decision.SecurityDecisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 详细设计-01 D.2 {@code POST /api/v1/security/decision} — 三通道唯一裁决入口
 * （operationId {@code decideDataAccess}）。页面/导出/AI 三通道 MUST 经本端点，
 * 直连 RLS/CLS/OPA 旁路置 0（导出旁路 grep=0 的结构性实现）。
 */
@RestController
@RequestMapping({"/api/v1/security/decision", "/api/security/decision"})
public class DecisionController {

    private static final Logger log = LoggerFactory.getLogger(DecisionController.class);

    private final SecurityDecisionService decisionService;

    public DecisionController(SecurityDecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> decide(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> ref = (Map<String, Object>) body.get("assetRef");
            Map<String, Object> subj = (Map<String, Object>) body.get("subject");
            String purposeRaw = String.valueOf(body.get("purpose"));

            if (ref == null || ref.get("table") == null) {
                return ApiResponse.badRequest("assetRef.table 必填");
            }
            if (purposeRaw.isBlank() || purposeRaw.equals("null")) {
                return ApiResponse.badRequest("purpose 必填（page|export|ai）");
            }

            AssetRef assetRef = new AssetRef(
                    ref.get("schema") != null ? ref.get("schema").toString() : "public",
                    ref.get("table").toString(),
                    ref.get("columns") instanceof List<?> cols ? cols.stream().map(String::valueOf).toList() : List.of());

            Purpose purpose;
            try {
                purpose = Purpose.valueOf(purposeRaw);
            } catch (IllegalArgumentException e) {
                return ApiResponse.badRequest("purpose 非法（page|export|ai）");
            }

            DecisionSubject subject;
            if (subj != null) {
                subject = new DecisionSubject(
                        str(subj.get("subjectId")),
                        str(subj.get("tenantId")),
                        str(subj.get("orgId")),
                        subj.get("clearance") instanceof Number n ? n.intValue() : null,
                        subj.get("roles") instanceof List<?> r ? r.stream().map(String::valueOf).toList() : null);
            } else {
                subject = new DecisionSubject(null);
            }

            Map<String, Object> bundle = decisionService.decide(assetRef, purpose, subject).toMap();
            return ApiResponse.success(bundle);
        } catch (SecurityDecisionService.SecurityUnavailableException e) {
            // C.6 降级矩阵：503 + 安全域错误码
            log.warn("decide DENY: code={}, {}", e.errorCode, e.getMessage());
            return ApiResponse.error(e.httpStatus, e.errorCode, e.getMessage());
        } catch (Exception e) {
            log.error("decide 失败", e);
            return ApiResponse.error(503, SecurityDecisionService.ERR_SECURITY_DOWN,
                    "security-engine 不可用，操作 DENY");
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
