package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.entity.Permission;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/system/permissions")
public class PermissionController {

    private static final Logger log = LoggerFactory.getLogger(PermissionController.class);
    private final IPermissionService permService;

    public PermissionController(IPermissionService permService) {
        this.permService = permService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        try {
            List<Permission> perms = permService.listPermissions();
            return ApiResponse.success(perms.stream().map(this::toMap).collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("查询权限列表失败", e);
            return ApiResponse.internalError("查询权限列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        try {
            Permission p = permService.getPermission(id);
            if (p == null) return ApiResponse.notFound("权限不存在");
            return ApiResponse.success(toMap(p));
        } catch (Exception e) {
            log.error("查询权限详情失败", e);
            return ApiResponse.internalError("查询权限详情失败: " + e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            Permission p = new Permission();
            p.setPermissionId(UUID.randomUUID().toString().replace("-", ""));
            p.setResource((String) body.get("resource"));
            p.setAction((String) body.get("action"));
            p.setConditionExpr((String) body.get("conditionExpr"));
            p.setDescription((String) body.get("description"));

            Permission created = permService.createPermission(p, "admin");
            return ApiResponse.success(toMap(created));
        } catch (Exception e) {
            log.error("创建权限失败", e);
            return ApiResponse.internalError("创建权限失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            Permission existing = permService.getPermission(id);
            if (existing == null) return ApiResponse.notFound("权限不存在");

            if (body.containsKey("resource")) existing.setResource((String) body.get("resource"));
            if (body.containsKey("action")) existing.setAction((String) body.get("action"));
            if (body.containsKey("description")) existing.setDescription((String) body.get("description"));
            if (body.containsKey("conditionExpr")) existing.setConditionExpr((String) body.get("conditionExpr"));

            Permission updated = permService.updatePermission(existing, "admin");
            return ApiResponse.success(toMap(updated));
        } catch (Exception e) {
            log.error("更新权限失败", e);
            return ApiResponse.internalError("更新权限失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        try {
            permService.deletePermission(id, "admin");
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除权限失败", e);
            return ApiResponse.internalError("删除权限失败: " + e.getMessage());
        }
    }

    private Map<String, Object> toMap(Permission p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("permissionId", p.getPermissionId());
        m.put("resource", p.getResource());
        m.put("action", p.getAction());
        m.put("conditionExpr", p.getConditionExpr());
        m.put("description", p.getDescription());
        return m;
    }
}
