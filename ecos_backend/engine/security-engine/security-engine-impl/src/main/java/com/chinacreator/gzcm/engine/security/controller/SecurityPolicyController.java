package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/security/policy", "/api/v1/security/policy"})
public class SecurityPolicyController {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyController.class);

    private final OpaPolicyService opaService;

    public SecurityPolicyController(OpaPolicyService opaService) {
        this.opaService = opaService;
    }

    /**
     * ABAC 策略评估端点。
     * 接收 ABAC 格式 (subject, resource, action, context)，
     * 转换为 OPA input map 后转发到 OpaPolicyService.evaluate()。
     */
    @PostMapping("/evaluate")
    public ApiResponse<Map<String, Object>> evaluate(@RequestBody Map<String, Object> body) {
        try {
            // 从请求体提取 ABAC 四元组
            @SuppressWarnings("unchecked")
            Map<String, Object> subject = (Map<String, Object>) body.get("subject");
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = (Map<String, Object>) body.get("resource");
            String action = (String) body.get("action");
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) body.get("context");

            // 构造 OPA input map
            Map<String, Object> opaInput = new LinkedHashMap<>();
            opaInput.put("subject", subject != null ? subject : Collections.emptyMap());
            opaInput.put("resource", resource != null ? resource : Collections.emptyMap());
            opaInput.put("action", action != null ? action : "");
            if (context != null) {
                opaInput.put("context", context);
            }

            // 转发到 OPA 策略服务评估
            Map<String, Object> evalResult = opaService.evaluate("abac", opaInput);

            // 包装返回
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("input", opaInput);
            result.put("result", evalResult);
            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("ABAC策略评估失败", e);
            return ApiResponse.internalError("ABAC评估失败: " + e.getMessage());
        }
    }
}
