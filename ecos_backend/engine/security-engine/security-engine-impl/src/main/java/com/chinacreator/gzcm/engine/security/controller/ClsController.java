package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.ColumnLevelSecurityServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/security/cls", "/api/v1/security/cls"})
public class ClsController {

    private static final Logger log = LoggerFactory.getLogger(ClsController.class);

    private final ColumnLevelSecurityServiceImpl clsService;

    public ClsController(ColumnLevelSecurityServiceImpl clsService) {
        this.clsService = clsService;
    }

    @PostMapping("/columns")
    public ApiResponse<Map<String, Object>> getColumns(@RequestBody Map<String, Object> body) {
        String tableName = (String) body.get("tableName");
        String userId = (String) body.get("userId");
        if (tableName == null || tableName.isBlank()) {
            return ApiResponse.badRequest("tableName不能为空");
        }
        if (userId == null || userId.isBlank()) {
            return ApiResponse.badRequest("userId不能为空");
        }
        @SuppressWarnings("unchecked")
        List<String> allColumns = (List<String>) body.get("allColumns");
        try {
            return ApiResponse.success(clsService.getColumns(tableName, userId, allColumns));
        } catch (Exception e) {
            log.error("CLS列权限查询失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/policies")
    public ApiResponse<List<Map<String, Object>>> listPolicies(
            @RequestParam(required = false) String tableName) {
        return ApiResponse.success(clsService.listPolicies(tableName));
    }

    @GetMapping("/policies/{id}")
    public ApiResponse<?> getPolicy(@PathVariable String id) {
        Map<String, Object> policy = clsService.getPolicy(id);
        if (policy == null) return ApiResponse.notFound("策略不存在: " + id);
        return ApiResponse.success(policy);
    }

    @PostMapping("/policies")
    public ApiResponse<?> createPolicy(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(clsService.createPolicy(body));
        } catch (Exception e) {
            log.error("创建CLS策略失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/policies/{id}")
    public ApiResponse<?> updatePolicy(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Map<String, Object> updated = clsService.updatePolicy(id, body);
        if (updated == null) return ApiResponse.notFound("策略不存在: " + id);
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/policies/{id}")
    public ApiResponse<?> deletePolicy(@PathVariable String id) {
        boolean deleted = clsService.deletePolicy(id);
        if (!deleted) return ApiResponse.notFound("策略不存在: " + id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("id", id);
        return ApiResponse.success(result);
    }
}
