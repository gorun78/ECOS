package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.service.predicate.RlsPredicateValidator.IllegalPredicateTemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/security/rls", "/api/v1/security/rls"})
public class RlsController {

    private static final Logger log = LoggerFactory.getLogger(RlsController.class);

    private final RowLevelSecurityServiceImpl rlsService;

    public RlsController(RowLevelSecurityServiceImpl rlsService) {
        this.rlsService = rlsService;
    }

    @PostMapping("/apply")
    public ApiResponse<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        String tableName = (String) body.get("tableName");
        String userId = (String) body.get("userId");
        if (tableName == null || tableName.isBlank()) {
            return ApiResponse.badRequest("tableName不能为空");
        }
        if (userId == null || userId.isBlank()) {
            return ApiResponse.badRequest("userId不能为空");
        }
        try {
            return ApiResponse.success(rlsService.apply(tableName, userId));
        } catch (Exception e) {
            log.error("RLS策略应用失败", e);
            return ApiResponse.internalError("策略应用失败");
        }
    }

    @GetMapping("/policies")
    public ApiResponse<List<Map<String, Object>>> listPolicies(
            @RequestParam(required = false) String tableName) {
        return ApiResponse.success(rlsService.listPolicies(tableName));
    }

    @GetMapping("/policies/{id}")
    public ApiResponse<?> getPolicy(@PathVariable String id) {
        Map<String, Object> policy = rlsService.getPolicy(id);
        if (policy == null) return ApiResponse.notFound("策略不存在: " + id);
        return ApiResponse.success(policy);
    }

    @PostMapping("/policies")
    public ApiResponse<?> createPolicy(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(rlsService.createPolicy(body));
        } catch (IllegalPredicateTemplateException e) {
            // ECOS-SEC-410：模板非法（消息不回显输入原文，B.3.1 行内提示可绑定变量清单）
            log.warn("RLS策略模板非法拒收: {}", e.getMessage());
            return ApiResponse.error(400, "ECOS-SEC-410", e.getMessage());
        } catch (Exception e) {
            log.error("创建RLS策略失败", e);
            return ApiResponse.internalError("创建失败");
        }
    }

    @PutMapping("/policies/{id}")
    public ApiResponse<?> updatePolicy(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> updated = rlsService.updatePolicy(id, body);
            if (updated == null) return ApiResponse.notFound("策略不存在: " + id);
            return ApiResponse.success(updated);
        } catch (IllegalPredicateTemplateException e) {
            log.warn("RLS策略模板非法拒收: id={}, {}", id, e.getMessage());
            return ApiResponse.error(400, "ECOS-SEC-410", e.getMessage());
        } catch (Exception e) {
            log.error("更新RLS策略失败", e);
            return ApiResponse.internalError("更新失败");
        }
    }

    @DeleteMapping("/policies/{id}")
    public ApiResponse<?> deletePolicy(@PathVariable String id) {
        Map<String, Object> result = rlsService.disablePolicy(id);
        if (result == null) return ApiResponse.notFound("策略不存在: " + id);
        return ApiResponse.success(result);
    }
}
