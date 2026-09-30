package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
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
        } catch (Exception e) {
            log.error("创建RLS策略失败", e);
            return ApiResponse.internalError("创建失败");
        }
    }

    @PutMapping("/policies/{id}")
    public ApiResponse<?> updatePolicy(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Map<String, Object> updated = rlsService.updatePolicy(id, body);
        if (updated == null) return ApiResponse.notFound("策略不存在: " + id);
        return ApiResponse.success(updated);
    }

    /**
     * 详细设计-01 D.5.2 {@code upsertRlsPolicy}：携 {@code predicateTemplate}+{@code bindings[]}，
     * 经 {@link com.chinacreator.gzcm.engine.security.rls.RlsPredicateValidator} 白名单校验，非法模板 400
     * （ECOS-SEC-410）。落 V164 新列，source_kind='parameterized'。
     */
    @PutMapping("/policies")
    public ApiResponse<?> upsertPolicy(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(rlsService.upsertPolicy(body));
        } catch (com.chinacreator.gzcm.engine.security.rls.RlsPredicateValidator.TemplateRejectedException e) {
            // 400 ECOS-SEC-410：谓词模板含非白名单字面量/关键字
            return ApiResponse.badRequest("非法谓词模板（ECOS-SEC-410）: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("upsert 参数化 RLS 策略失败", e);
            return ApiResponse.internalError("upsert 失败");
        }
    }

    @DeleteMapping("/policies/{id}")
    public ApiResponse<?> deletePolicy(@PathVariable String id) {
        boolean deleted = rlsService.deletePolicy(id);
        if (!deleted) return ApiResponse.notFound("策略不存在: " + id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("id", id);
        return ApiResponse.success(result);
    }
}
