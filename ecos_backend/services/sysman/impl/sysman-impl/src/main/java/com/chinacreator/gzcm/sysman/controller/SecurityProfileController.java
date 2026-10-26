package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.iam.context.TenantContext;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import com.chinacreator.gzcm.sysman.service.SecurityProfileQueryService;

/**
 * ECOS 安全Profile控制器 — 用户/角色级安全Profile绑定。
 * <p>
 * 优先级：User级配置 > Role级配置 > 全局默认 (isDefault=true)
 * </p>
 * <p>
 * 从 SecurityConfigController 拆分出来（原 983 行拆为两个 Controller）。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/security-profiles")
@MinimumClearance(level = 2)
public class SecurityProfileController {

        private final SecurityProfileQueryService securityProfileQueryService;
private static final Logger log = LoggerFactory.getLogger(SecurityProfileController.class);

    public SecurityProfileController(SecurityProfileQueryService securityProfileQueryService) {
        this.securityProfileQueryService = securityProfileQueryService;
    }

    /**
     * 获取当前租户ID — 优先从 UserContext 获取，回退到 TenantContext。
     */
    private String getCurrentTenantId() {
        try {
            String tid = UserContext.getCurrentTenantId();
            if (tid != null && !tid.isBlank()) {
                return tid;
            }
        } catch (Exception e) {
            log.debug("从 UserContext 获取 tenantId 失败: {}", e.getMessage());
        }
        try {
            return TenantContext.getTenantId();
        } catch (Exception e) {
            log.debug("从 TenantContext 获取 tenantId 失败: {}", e.getMessage());
            return null;
        }
    }

    // ────────────────────────────────────────────────
    // 核心 API：当前用户/角色的安全配置
    // ────────────────────────────────────────────────

    /**
     * 获取安全配置 — 按级联优先级返回。
     * 可选参数：?userId=xxx 或 ?roleId=xxx 或 ?scopeType=TENANT&scopeId=xxx
     * 不传参数时从 UserContext 获取当前用户。
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> getProfile(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String roleId,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId) {
        try {
            // 如果指定了 scopeType，按作用域查询
            if (scopeType != null && !scopeType.isBlank()) {
                SecurityProfile profile = queryProfileByScope(scopeType.toUpperCase(), scopeId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("source", "scope_" + scopeType.toLowerCase());
                populateProfileResult(result, profile);
                return ApiResponse.success(result);
            }

            // 优先级：参数 userId > UserContext userId
            String effectiveUserId = userId != null ? userId : UserContext.getCurrentUserId();

            SecurityProfile profile = null;
            String source = "global_default";

            // 1. 查用户级配置
            if (effectiveUserId != null) {
                profile = queryUserProfile(effectiveUserId);
                if (profile != null) {
                    source = "user";
                }
            }

            // 2. 查角色级配置（取最高级角色）
            if (profile == null && roleId != null) {
                profile = queryRoleProfile(roleId);
                if (profile != null) {
                    source = "role";
                }
            }
            if (profile == null && effectiveUserId != null) {
                profile = queryHighestRoleProfileForUser(effectiveUserId);
                if (profile != null) {
                    source = "role";
                }
            }

            // 3. 查全局默认
            if (profile == null) {
                profile = queryGlobalDefaultProfile();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", source);
            populateProfileResult(result, profile);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取安全配置失败", e);
            return ApiResponse.internalError("获取安全配置失败");
        }
    }

    /**
     * 更新安全配置 — 支持按 userId 或 roleId 写入对应表。
     */
    @PutMapping
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<Map<String, Object>> updateProfile(
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String roleId) {
        try {
            String effectiveUserId = userId != null ? userId : UserContext.getCurrentUserId();

            // 确定写入哪一档（#142/B4-3e-87：具名目标档，语句单源在属主 DAO）
            String profileTarget;
            String idColumn;
            String idValue;
            boolean isGlobal = false;

            if (effectiveUserId != null) {
                profileTarget = "USER";
                idColumn = "user_id";
                idValue = effectiveUserId;
            } else if (roleId != null) {
                profileTarget = "ROLE";
                idColumn = "role_id";
                idValue = roleId;
            } else {
                // 写入全局默认（user_id = '_global_default_'）
                profileTarget = "USER";
                idColumn = "user_id";
                idValue = "_global_default_";
                isGlobal = true;
            }

            // 读取请求参数
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

            int rows = securityProfileQueryService.upsertCoalescing(profileTarget, idValue,
                    clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory,
                    isGlobal, scopeType, bodyTenantId, orgId);

            log.info("安全配置更新成功: target={}, id={}, rows={}, scopeType={}", profileTarget, idValue, rows, scopeType);

            // 返回更新后的配置
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("table", "USER".equals(profileTarget) ? "td_user_security_profile" : "td_role_security_profile");
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
            return ApiResponse.internalError("更新安全配置失败");
        }
    }

    // ────────────────────────────────────────────────
    // 按用户/角色查询安全配置
    // ────────────────────────────────────────────────

    /**
     * 查询指定用户的安全配置。
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<?> getUserProfile(@PathVariable String userId) {
        try {
            SecurityProfile profile = queryUserProfile(userId);
            if (profile == null) {
                return ApiResponse.notFound("用户安全配置不存在: " + userId);
            }
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("查询用户安全配置失败: userId={}", userId, e);
            return ApiResponse.internalError("查询失败");
        }
    }

    /**
     * 更新指定用户的安全配置（UPSERT）。
     */
    @PutMapping("/user/{userId}")
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

            // #142/B4-3e-87：语句单源收口属主 DAO（原内联 15 参 upsert 已删除）
            securityProfileQueryService.upsertUserProfile(userId,
                    clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory,
                    false, scopeType, bodyTenantId, orgId);

            log.info("用户安全配置更新成功: userId={}, scopeType={}", userId, scopeType);

            SecurityProfile updated = queryUserProfile(userId);
            return ApiResponse.success(updated != null ? updated : Map.of("userId", userId, "success", true));
        } catch (Exception e) {
            log.error("更新用户安全配置失败: userId={}", userId, e);
            return ApiResponse.internalError("更新失败");
        }
    }

    /**
     * 查询指定角色的安全配置。
     */
    @GetMapping("/role/{roleId}")
    public ApiResponse<?> getRoleProfile(@PathVariable String roleId) {
        try {
            SecurityProfile profile = queryRoleProfile(roleId);
            if (profile == null) {
                return ApiResponse.notFound("角色安全配置不存在: " + roleId);
            }
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("查询角色安全配置失败: roleId={}", roleId, e);
            return ApiResponse.internalError("查询失败");
        }
    }

    /**
     * 更新指定角色的安全配置（UPSERT）。
     */
    @PutMapping("/role/{roleId}")
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

            // #142/B4-3e-87：语句单源收口属主 DAO（原内联 15 参 upsert 已删除）
            securityProfileQueryService.upsertRoleProfile(roleId,
                    clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory,
                    scopeType, bodyTenantId, orgId);

            log.info("角色安全配置更新成功: roleId={}, scopeType={}", roleId, scopeType);

            SecurityProfile updated = queryRoleProfile(roleId);
            return ApiResponse.success(updated != null ? updated : Map.of("roleId", roleId, "success", true));
        } catch (Exception e) {
            log.error("更新角色安全配置失败: roleId={}", roleId, e);
            return ApiResponse.internalError("更新失败");
        }
    }

    /**
     * 列出所有角色级安全配置。
     */
    @GetMapping("/roles")
    public ApiResponse<?> listRoleProfiles() {
        try {
            List<SecurityProfile> list = queryAllRoleProfiles();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询角色安全配置列表失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    /**
     * 列出所有用户级安全配置。
     */
    @GetMapping("/users")
    public ApiResponse<?> listUserProfiles() {
        try {
            List<SecurityProfile> list = queryAllUserProfiles();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("查询用户安全配置列表失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    // ────────────────────────────────────────────────
    // 辅助查询方法
    // ────────────────────────────────────────────────

    private SecurityProfile queryUserProfile(String userId) {
        try {
            return securityProfileQueryService.findUser(userId, getCurrentTenantId());
        } catch (Exception e) {
            log.warn("查询用户安全配置异常: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    private SecurityProfile queryRoleProfile(String roleId) {
        try {
            return securityProfileQueryService.findRole(roleId, getCurrentTenantId());
        } catch (Exception e) {
            log.warn("查询角色安全配置异常: roleId={}, {}", roleId, e.getMessage());
            return null;
        }
    }

    private SecurityProfile queryHighestRoleProfileForUser(String userId) {
        try {
            return securityProfileQueryService.findHighestRoleForUser(userId, getCurrentTenantId());
        } catch (Exception e) {
            log.warn("查询用户最高角色安全配置异常: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    private SecurityProfile queryGlobalDefaultProfile() {
        try {
            return securityProfileQueryService.findGlobalDefault(getCurrentTenantId());
        } catch (Exception e) {
            log.warn("查询全局默认安全配置异常: {}", e.getMessage());
            return null;
        }
    }

    private List<SecurityProfile> queryAllUserProfiles() {
        try {
            return securityProfileQueryService.listAllUser(getCurrentTenantId());
        } catch (Exception e) {
            log.warn("查询所有用户安全配置异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SecurityProfile> queryAllRoleProfiles() {
        try {
            return securityProfileQueryService.listAllRole(getCurrentTenantId());
        } catch (Exception e) {
            log.warn("查询所有角色安全配置异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ────────────────────────────────────────────────
    // 作用域查询方法
    // ────────────────────────────────────────────────

    /**
     * 按作用域类型查询安全配置。
     */
    private SecurityProfile queryProfileByScope(String scopeType, String scopeId) {
        try {
            switch (scopeType) {
                case "TENANT":
                case "ORG":
                    SecurityProfile profile = securityProfileQueryService.findUserByScope(scopeType, scopeId);
                    return profile;
                default:
                    log.debug("未知作用域类型: {}", scopeType);
                    return null;
            }
        } catch (Exception e) {
            log.warn("按作用域查询安全配置异常: scopeType={}, scopeId={}, {}", scopeType, scopeId, e.getMessage());
            return null;
        }
    }

    /**
     * 将 SecurityProfile 填入结果 Map（复用公共逻辑）。
     */
    private void populateProfileResult(Map<String, Object> result, SecurityProfile profile) {
        if (profile != null) {
            result.put("clearanceLevel", profile.getClearanceLevel() != null ? profile.getClearanceLevel() : 1);
            result.put("level", profile.getLevel() != null ? profile.getLevel() : 1);
            result.put("levelName", profile.getLevelName() != null ? profile.getLevelName() : "L1内部");
            result.put("linkedWorkstation", profile.getLinkedWorkstation() != null ? profile.getLinkedWorkstation() : "");
            result.put("auditMode", profile.getAuditMode() != null ? profile.getAuditMode() : "basic");
            result.put("sandboxMandatory", Boolean.TRUE.equals(profile.getSandboxMandatory()));
            if (profile.getUserId() != null) result.put("userId", profile.getUserId());
            if (profile.getRoleId() != null) result.put("roleId", profile.getRoleId());
            if (profile.getScopeType() != null) result.put("scopeType", profile.getScopeType());
            if (profile.getTenantId() != null) result.put("tenantId", profile.getTenantId());
            if (profile.getOrgId() != null) result.put("orgId", profile.getOrgId());
        } else {
            result.put("clearanceLevel", 1);
            result.put("level", 1);
            result.put("levelName", "L1内部");
            result.put("linkedWorkstation", "");
            result.put("auditMode", "basic");
            result.put("sandboxMandatory", false);
        }
    }
}
