package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.GuardrailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/guardrails")
public class GuardrailsApiController {

    private static final Logger log = LoggerFactory.getLogger(GuardrailsApiController.class);

    private final GuardrailsService guardrailsService;

    public GuardrailsApiController(GuardrailsService guardrailsService) {
        this.guardrailsService = guardrailsService;
    }

    @PostMapping("/validate")
    public ApiResponse<Map<String, Object>> validate(@RequestBody Map<String, Object> req) {
        Map<String, Object> result = guardrailsService.validate(req);
        return ApiResponse.success(result);
    }

    @GetMapping("/policies")
    public ApiResponse<List<Map<String, Object>>> listPolicies() {
        return ApiResponse.success(guardrailsService.listPolicies());
    }

    @PostMapping("/policies")
    public ApiResponse<Map<String, Object>> createPolicy(@RequestBody Map<String, Object> body) {
        if (body == null) return ApiResponse.badRequest("请求体不能为空");
        Map<String, Object> policy = guardrailsService.createPolicy(body);
        return ApiResponse.success(policy);
    }

    @PutMapping("/policies/{id}")
    public ApiResponse<Map<String, Object>> updatePolicy(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return ApiResponse.notFound("Use POST /policies to create new policies");
    }

    @DeleteMapping("/policies/{id}")
    public ApiResponse<Map<String, Object>> deletePolicy(@PathVariable String id) {
        guardrailsService.deletePolicy(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("deleted", true);
        return ApiResponse.success(result);
    }

    @PostMapping("/policies/{id}/compile")
    public ApiResponse<Map<String, Object>> compilePolicy(@PathVariable String id) {
        log.info("Guardrails policy compiled (placeholder): id={}", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", "compiled");
        return ApiResponse.success(result);
    }

    @GetMapping("/policies/{id}/preview")
    public ApiResponse<List<Object>> previewPolicy(@PathVariable String id) {
        return ApiResponse.success(Collections.emptyList());
    }
}
