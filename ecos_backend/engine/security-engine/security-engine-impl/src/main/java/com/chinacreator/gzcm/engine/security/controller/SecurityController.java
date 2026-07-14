package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
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

    @PostMapping("/evaluate-filter")
    public ApiResponse<Map<String, Object>> evaluateFilter(@RequestBody Map<String, Object> body) {
        String expression = (String) body.getOrDefault("expression", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> rowData = (Map<String, Object>) body.getOrDefault("rowData", Map.of());
        String userRole = (String) body.getOrDefault("userRole", "viewer");
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
