package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/policy-engine")
public class PolicyEngineController {
    private static final Logger log = LoggerFactory.getLogger(PolicyEngineController.class);

    private final OpaPolicyService service;

    public PolicyEngineController(OpaPolicyService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.success(service.getStatus());
    }

    @PostMapping("/evaluate")
    public ApiResponse<Map<String, Object>> evaluate(@RequestBody Map<String, Object> body) {
        String policy = (String) body.getOrDefault("policy", "rbac");
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) body.get("input");
        return ApiResponse.success(service.evaluate(policy, input));
    }

    @GetMapping("/policies")
    public ApiResponse<List<String>> listPolicies() {
        return ApiResponse.success(service.listPolicies());
    }

    @GetMapping("/policies/{name}")
    public ApiResponse<Map<String, String>> getPolicy(@PathVariable String name) {
        Map<String, String> policy = service.getPolicy(name);
        if (policy == null) return ApiResponse.notFound("策略 " + name + " 不存在");
        return ApiResponse.success(policy);
    }

    @PutMapping("/policies/{name}")
    public ApiResponse<Map<String, String>> updatePolicy(@PathVariable String name,
                                                          @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank())
            return ApiResponse.badRequest("缺少 content 字段");
        try {
            return ApiResponse.success(service.updatePolicy(name, content));
        } catch (Exception e) {
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }
}
