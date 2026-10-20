package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.filter.RowFilterVerdictContract;
import com.chinacreator.gzcm.engine.security.service.SecuritySandboxService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private final SecuritySandboxService service;

    public SecurityController(SecuritySandboxService service) {
        this.service = service;
    }

    @PostMapping("/mask")
    public ApiResponse<Map<String, Object>> mask(@RequestBody Map<String, Object> body) {
        String value = (String) body.getOrDefault("value", "");
        String maskType = (String) body.getOrDefault("maskType", "SHA256");
        return ApiResponse.success(service.maskValue(value, maskType));
    }

    /**
     * 行过滤端点（承流＝本模块 {@link SecuritySandboxService}，键面属主＝
     * {@link RowFilterVerdictContract}）。本 Controller 只做请求键读取与透传，
     * 不组装任何裁决字段 —— 裁决外形由服务侧的契约工厂单源持有。
     */
    @PostMapping("/evaluate-filter")
    public ApiResponse<Map<String, Object>> evaluateFilter(@RequestBody Map<String, Object> body) {
        String expression = (String) body.getOrDefault(RowFilterVerdictContract.KEY_EXPRESSION, "");
        @SuppressWarnings("unchecked")
        Map<String, Object> rowData = (Map<String, Object>) body.getOrDefault(
                RowFilterVerdictContract.REQ_KEY_ROW_DATA, Map.of());
        String userRole = (String) body.getOrDefault(
                RowFilterVerdictContract.REQ_KEY_USER_ROLE, RowFilterVerdictContract.VALUE_DEFAULT_ROLE);
        return ApiResponse.success(service.evaluateFilter(expression, rowData, userRole));
    }

    @PostMapping("/decrypt")
    public ApiResponse<Map<String, Object>> decrypt(@RequestBody Map<String, Object> body) {
        String userId = (String) body.getOrDefault("userId", "unknown");
        String orgId = (String) body.getOrDefault("orgId", "");
        String datasetId = (String) body.getOrDefault("datasetId", "");
        return ApiResponse.success(service.decrypt(userId, orgId, datasetId));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<Map<String, Object>>> getAuditLogs() {
        return ApiResponse.success(service.getAuditLogs());
    }

    @GetMapping("/audit")
    public ApiResponse<Map<String, Object>> getDiagnosticReport() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("diagnosticReport", service.getDiagnosticReport());
        return ApiResponse.success(result);
    }
}
