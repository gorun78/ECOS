package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.entity.Permission;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限项 CRUD（{@code /api/v1/system/permissions}）。
 *
 * <p><b>#147BZ（DoD DI-2 第五批）</b>：请求体由弱类型 Map 改 {@link PermissionRequest}，
 * 响应由 {@code toMap} 改 {@link PermissionVO}。wire 键面逐字不变：5 键
 * {@code permissionId/resource/action/conditionExpr/description}，null 值键仍输出。</p>
 *
 * <p><b>更新语义保真</b>：旧实现用 {@code body.containsKey(...)} 判定「是否提供」，故显式
 * {@code null} 会被写空。DTO 用 {@code xxxPresent} 标记（Jackson 对显式 null 仍调用 setter，
 * 缺席不调用），语义逐字一致——禁退化成「null 即不更新」。</p>
 */
@RestController
@RequestMapping("/api/v1/system/permissions")
public class PermissionController {

    private static final Logger log = LoggerFactory.getLogger(PermissionController.class);
    private final IPermissionService permService;

    public PermissionController(IPermissionService permService) {
        this.permService = permService;
    }

    @GetMapping
    public ApiResponse<List<PermissionVO>> list() {
        try {
            List<Permission> perms = permService.listPermissions();
            return ApiResponse.success(perms.stream().map(PermissionController::toVO)
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("查询权限列表失败", e);
            return ApiResponse.internalError("查询权限列表失败");
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<PermissionVO> get(@PathVariable String id) {
        try {
            Permission p = permService.getPermission(id);
            if (p == null) return ApiResponse.notFound("权限不存在");
            return ApiResponse.success(toVO(p));
        } catch (Exception e) {
            log.error("查询权限详情失败", e);
            return ApiResponse.internalError("查询权限详情失败");
        }
    }

    @PostMapping
    public ApiResponse<PermissionVO> create(@RequestBody PermissionRequest body) {
        try {
            Permission p = new Permission();
            p.setPermissionId(UUID.randomUUID().toString().replace("-", ""));
            p.setResource(body.getResource());
            p.setAction(body.getAction());
            p.setConditionExpr(body.getConditionExpr());
            p.setDescription(body.getDescription());

            Permission created = permService.createPermission(p, "admin");
            return ApiResponse.success(toVO(created));
        } catch (Exception e) {
            log.error("创建权限失败", e);
            return ApiResponse.internalError("创建权限失败");
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<PermissionVO> update(@PathVariable String id, @RequestBody PermissionRequest body) {
        try {
            Permission existing = permService.getPermission(id);
            if (existing == null) return ApiResponse.notFound("权限不存在");

            if (body.isResourcePresent()) existing.setResource(body.getResource());
            if (body.isActionPresent()) existing.setAction(body.getAction());
            if (body.isDescriptionPresent()) existing.setDescription(body.getDescription());
            if (body.isConditionExprPresent()) existing.setConditionExpr(body.getConditionExpr());

            Permission updated = permService.updatePermission(existing, "admin");
            return ApiResponse.success(toVO(updated));
        } catch (Exception e) {
            log.error("更新权限失败", e);
            return ApiResponse.internalError("更新权限失败");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        try {
            permService.deletePermission(id, "admin");
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除权限失败", e);
            return ApiResponse.internalError("删除权限失败");
        }
    }

    static PermissionVO toVO(Permission p) {
        return new PermissionVO(p.getPermissionId(), p.getResource(), p.getAction(),
                p.getConditionExpr(), p.getDescription());
    }

    /** 权限项请求（5 键同名；{@code xxxPresent} 保留旧 {@code containsKey} 更新语义）。 */
    public static final class PermissionRequest {
        private String resource;
        private String action;
        private String conditionExpr;
        private String description;
        private boolean resourcePresent;
        private boolean actionPresent;
        private boolean conditionExprPresent;
        private boolean descriptionPresent;

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
            this.resourcePresent = true;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
            this.actionPresent = true;
        }

        public String getConditionExpr() {
            return conditionExpr;
        }

        public void setConditionExpr(String conditionExpr) {
            this.conditionExpr = conditionExpr;
            this.conditionExprPresent = true;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
            this.descriptionPresent = true;
        }

        public boolean isResourcePresent() {
            return resourcePresent;
        }

        public boolean isActionPresent() {
            return actionPresent;
        }

        public boolean isConditionExprPresent() {
            return conditionExprPresent;
        }

        public boolean isDescriptionPresent() {
            return descriptionPresent;
        }
    }

    /** 权限项响应（键集与旧 Map 逐字一致）。 */
    public record PermissionVO(String permissionId, String resource, String action,
                               String conditionExpr, String description) {
    }
}
