package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.SecurityConfigService;
import com.chinacreator.gzcm.sysman.controller.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/security")
public class SecurityConfigController {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfigController.class);

    private final SecurityConfigService service;

    public SecurityConfigController(SecurityConfigService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getProfile(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String roleId,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId) {
        try {
            if (scopeType != null && !scopeType.isBlank()) {
                SecurityProfile profile = service.queryProfileByScope(scopeType.toUpperCase(), scopeId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("source", "scope_" + scopeType.toLowerCase());
                result.putAll(service.populateProfileResult(profile));
                return ApiResponse.success(result);
            }

            String effectiveUserId = userId != null ? userId : UserContext.getCurrentUserId();

            SecurityProfile profile = null;
            String source = "global_default";

            if (effectiveUserId != null) {
                profile = service.queryUserProfile(effectiveUserId);
                if (profile != null) {
                    source = "user";
                }
            }

            if (profile == null && roleId != null) {
                profile = service.queryRoleProfile(roleId);
                if (profile != null) {
                    source = "role";
                }
            }
            if (profile == null && effectiveUserId != null) {
                profile = service.queryHighestRoleProfileForUser(effectiveUserId);
                if (profile != null) {
                    source = "role";
                }
            }

            if (profile == null) {
                profile = service.queryGlobalDefaultProfile();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", source);
            result.putAll(service.populateProfileResult(profile));
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取安全配置失败", e);
            return ApiResponse.internalError("获取安全配置失败: " + e.getMessage());
        }
    }

    @PutMapping("/profile")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<Map<String, Object>> updateProfile(
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String roleId) {
        try {
            String effectiveUserId = userId != null ? userId : UserContext.getCurrentUserId();

            String targetTable;
            String idColumn;
            String idValue;
            boolean isGlobal = false;

            if (effectiveUserId != null) {
                targetTable = "td_user_security_profile";
                idColumn = "user_id";
                idValue = effectiveUserId;
            } else if (roleId != null) {
                targetTable = "td_role_security_profile";
                idColumn = "role_id";
                idValue = roleId;
            } else {
                targetTable = "td_user_security_profile";
                idColumn = "user_id";
                idValue = "_global_default_";
                isGlobal = true;
            }

            Integer clearanceLevel = body.containsKey("clearanceLevel")
                    ? ((Number) body.get("clearanceLevel")).intValue() : null;
            String linkedWorkstation = body.containsKey("linkedWorkstation")
                    ? (String) body.get("linkedWorkstation") : null;
            String auditMode = body.containsKey("auditMode")
                    ? (String) body.get("auditMode") : null;
            Boolean sandboxMandatory = body.containsKey("sandboxMandatory")
                    ? Boolean.TRUE.equals(body.get("sandboxMandatory")) : null;
            String scopeType = body.containsKey("scopeType")
                    ? (String) body.get("scopeType") : null;
            String bodyTenantId = body.containsKey("tenantId")
                    ? (String) body.get("tenantId") : null;
            String orgId = body.containsKey("orgId")
                    ? (String) body.get("orgId") : null;

            service.upsertProfile(targetTable, idColumn, idValue,
                    clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isGlobal,
                    scopeType, bodyTenantId, orgId);

            log.info("安全配置更新成功: table={}, id={}, scopeType={}", targetTable, idValue, scopeType);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("table", targetTable);
            result.put(idColumn, idValue);
            if (clearanceLevel != null) result.put("clearanceLevel", clearanceLevel);
            if (linkedWorkstation != null) result.put("linkedWorkstation", linkedWorkstation);
            if (auditMode != null) result.put("auditMode", auditMode);
            if (sandboxMandatory != null) result.put("sandboxMandatory", sandboxMandatory);
            if (scopeType != null) result.put("scopeType", scopeType);
            if (bodyTenantId != null) result.put("tenantId", bodyTenantId);
            if (orgId != null) result.put("orgId", orgId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("更新安全配置失败", e);
            return ApiResponse.internalError("更新安全配置失败: " + e.getMessage());
        }
    }

    @GetMapping("/profile/user/{userId}")
    public ApiResponse<?> getUserProfile(@PathVariable String userId) {
        try {
            SecurityProfile profile = service.queryUserProfile(userId);
            if (profile == null) {
                return ApiResponse.notFound("用户安全配置不存在: " + userId);
            }
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("查询用户安全配置失败: userId={}", userId, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @PutMapping("/profile/user/{userId}")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> updateUserProfile(@PathVariable String userId,
                                            @RequestBody Map<String, Object> body) {
        try {
            Integer clearanceLevel = body.containsKey("clearanceLevel")
                    ? ((Number) body.get("clearanceLevel")).intValue() : null;
            String linkedWorkstation = body.containsKey("linkedWorkstation")
                    ? (String) body.get("linkedWorkstation") : null;
            String auditMode = body.containsKey("auditMode")
                    ? (String) body.get("auditMode") : null;
            Boolean sandboxMandatory = body.containsKey("sandboxMandatory")
                    ? (Boolean) body.get("sandboxMandatory") : null;
            String scopeType = body.containsKey("scopeType")
                    ? (String) body.get("scopeType") : null;
            String bodyTenantId = body.containsKey("tenantId")
                    ? (String) body.get("tenantId") : null;
            String orgId = body.containsKey("orgId")
                    ? (String) body.get("orgId") : null;

            service.upsertUserProfile(userId, clearanceLevel, linkedWorkstation,
                    auditMode, sandboxMandatory, false, scopeType, bodyTenantId, orgId);

            log.info("用户安全配置更新成功: userId={}, scopeType={}", userId, scopeType);

            SecurityProfile updated = service.queryUserProfile(userId);
            return ApiResponse.success(updated != null ? updated : Map.of("userId", userId, "success", true));
        } catch (Exception e) {
            log.error("更新用户安全配置失败: userId={}", userId, e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    @GetMapping("/profile/role/{roleId}")
    public ApiResponse<?> getRoleProfile(@PathVariable String roleId) {
        try {
            SecurityProfile profile = service.queryRoleProfile(roleId);
            if (profile == null) {
                return ApiResponse.notFound("角色安全配置不存在: " + roleId);
            }
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("查询角色安全配置失败: roleId={}", roleId, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @PutMapping("/profile/role/{roleId}")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> updateRoleProfile(@PathVariable String roleId,
                                            @RequestBody Map<String, Object> body) {
        try {
            Integer clearanceLevel = body.containsKey("clearanceLevel")
                    ? ((Number) body.get("clearanceLevel")).intValue() : null;
            String linkedWorkstation = body.containsKey("linkedWorkstation")
                    ? (String) body.get("linkedWorkstation") : null;
            String auditMode = body.containsKey("auditMode")
                    ? (String) body.get("auditMode") : null;
            Boolean sandboxMandatory = body.containsKey("sandboxMandatory")
                    ? (Boolean) body.get("sandboxMandatory") : null;
            String scopeType = body.containsKey("scopeType")
                    ? (String) body.get("scopeType") : null;
            String bodyTenantId = body.containsKey("tenantId")
                    ? (String) body.get("tenantId") : null;
            String orgId = body.containsKey("orgId")
                    ? (String) body.get("orgId") : null;

            service.upsertRoleProfile(roleId, clearanceLevel, linkedWorkstation,
                    auditMode, sandboxMandatory, scopeType, bodyTenantId, orgId);

            log.info("角色安全配置更新成功: roleId={}, scopeType={}", roleId, scopeType);

            SecurityProfile updated = service.queryRoleProfile(roleId);
            return ApiResponse.success(updated != null ? updated : Map.of("roleId", roleId, "success", true));
        } catch (Exception e) {
            log.error("更新角色安全配置失败: roleId={}", roleId, e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    @GetMapping("/profile/roles")
    public ApiResponse<?> listRoleProfiles() {
        try {
            return ApiResponse.success(service.queryAllRoleProfiles());
        } catch (Exception e) {
            log.error("查询角色安全配置列表失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/profile/users")
    public ApiResponse<?> listUserProfiles() {
        try {
            return ApiResponse.success(service.queryAllUserProfiles());
        } catch (Exception e) {
            log.error("查询用户安全配置列表失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/profiles")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId) {
        try {
            List<SecurityProfile> all;
            if (scopeType != null && !scopeType.isBlank()) {
                all = service.queryProfilesByScope(scopeType, scopeId);
            } else {
                all = new ArrayList<>();
                all.addAll(service.queryAllUserProfiles());
                all.addAll(service.queryAllRoleProfiles());
            }

            if (keyword != null && !keyword.isEmpty()) {
                String kw = keyword.toLowerCase();
                all = all.stream()
                        .filter(p -> (p.getUserId() != null && p.getUserId().toLowerCase().contains(kw))
                                || (p.getRoleId() != null && p.getRoleId().toLowerCase().contains(kw))
                                || (p.getAuditMode() != null && p.getAuditMode().toLowerCase().contains(kw)))
                        .collect(Collectors.toList());
            }

            int from = (page - 1) * pageSize;
            int to = Math.min(from + pageSize, all.size());
            List<SecurityProfile> pageList = from < all.size()
                    ? all.subList(from, to)
                    : Collections.emptyList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", pageList);
            result.put("total", all.size());
            result.put("page", page);
            result.put("pageSize", pageSize);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询安全配置列表失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/profiles/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        try {
            SecurityProfile profile = service.queryUserProfile(id);
            if (profile == null) {
                return ApiResponse.notFound("安全配置不存在: " + id);
            }
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("查询安全配置失败, id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/profiles/active")
    public ApiResponse<?> getActive() {
        try {
            SecurityProfile active = service.queryGlobalDefaultProfile();
            if (active == null) {
                return ApiResponse.notFound("未找到激活的安全配置");
            }
            return ApiResponse.success(active);
        } catch (Exception e) {
            log.error("查询激活的安全配置失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/profiles")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> create(@RequestBody SecurityProfile profile) {
        try {
            String id = profile.getUserId();
            if (id == null || id.isBlank()) {
                id = UUID.randomUUID().toString().replace("-", "");
            }

            boolean isDefault = Boolean.TRUE.equals(profile.getIsDefault());

            if (isDefault) {
                service.clearAllDefaults();
            }

            service.upsertUserProfile(id, profile.getClearanceLevel(), profile.getLinkedWorkstation(),
                    profile.getAuditMode(), profile.getSandboxMandatory(), isDefault,
                    profile.getScopeType(), profile.getTenantId(), profile.getOrgId());

            log.info("安全配置模板创建成功, id={}", id);

            SecurityProfile created = service.queryUserProfile(id);
            return ApiResponse.success(created != null ? created : profile);
        } catch (Exception e) {
            log.error("创建安全配置失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/profiles/{id}")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody SecurityProfile profile) {
        try {
            SecurityProfile existing = service.queryUserProfile(id);
            if (existing == null) {
                existing = service.queryRoleProfile(id);
                if (existing == null) {
                    return ApiResponse.notFound("安全配置不存在: " + id);
                }
            }

            boolean isDefault = Boolean.TRUE.equals(profile.getIsDefault());
            if (isDefault) {
                service.clearAllDefaults();
            }

            service.updateProfileFields(id, profile.getClearanceLevel(), profile.getLinkedWorkstation(),
                    profile.getAuditMode(), profile.getSandboxMandatory(), isDefault);

            log.info("安全配置模板更新成功, id={}", id);
            SecurityProfile updated = service.queryUserProfile(id);
            return ApiResponse.success(updated != null ? updated : profile);
        } catch (Exception e) {
            log.error("更新安全配置失败, id={}", id, e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/profiles/{id}")
    @RequirePermission(permission = "security:config:delete")
    public ApiResponse<?> delete(@PathVariable String id) {
        try {
            if ("_global_default_".equals(id)) {
                return ApiResponse.badRequest("不允许删除全局默认安全配置");
            }

            int rows = service.deleteUserProfile(id);
            if (rows == 0) {
                rows = service.deleteRoleProfile(id);
            }
            if (rows == 0) {
                return ApiResponse.notFound("安全配置不存在: " + id);
            }

            log.info("安全配置删除成功, id={}", id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("id", id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("删除安全配置失败, id={}", id, e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/profiles/{id}/activate")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> activate(@PathVariable String id) {
        try {
            service.clearAllDefaults();
            service.activateProfile(id);

            log.info("安全配置已激活, id={}", id);
            SecurityProfile profile = service.queryUserProfile(id);
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("激活安全配置失败, id={}", id, e);
            return ApiResponse.internalError("激活失败: " + e.getMessage());
        }
    }

    @PostMapping("/profiles/{id}/clone")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> clone(@PathVariable String id,
                                @RequestParam(required = false) String newName) {
        try {
            String newId = UUID.randomUUID().toString().replace("-", "");
            service.cloneProfile(id, newId);

            log.info("安全配置克隆成功, sourceId={}, newId={}", id, newId);
            SecurityProfile cloned = service.queryUserProfile(newId);
            return ApiResponse.success(cloned);
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("克隆安全配置失败, sourceId={}", id, e);
            return ApiResponse.internalError("克隆失败: " + e.getMessage());
        }
    }
}
