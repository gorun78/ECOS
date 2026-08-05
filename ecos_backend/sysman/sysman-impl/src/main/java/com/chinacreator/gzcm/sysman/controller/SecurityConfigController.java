package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.controller.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.iam.context.TenantContext;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ECOS 安全配置控制器 — 安全配置模板 CRUD（列表/详情/创建/更新/删除/激活/克隆）。
 * <p>
 * 用户/角色级安全Profile绑定已拆分至 {@link SecurityProfileController}。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/security")
@MinimumClearance(level = 2)
public class SecurityConfigController {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfigController.class);

    private static final String[] LEVEL_NAMES = {"L0公开", "L1内部", "L2保密", "L3机密", "L4绝密"};

    private final JdbcTemplate jdbc;

    public SecurityConfigController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ────────────────────────────────────────────────
    // RowMapper
    // ────────────────────────────────────────────────

    private final RowMapper<SecurityProfile> USER_PROFILE_MAPPER = (rs, rowNum) -> {
        SecurityProfile p = new SecurityProfile();
        p.setUserId(rs.getString("user_id"));
        p.setClearanceLevel(rs.getInt("clearance_level"));
        p.setLevel(rs.getInt("clearance_level"));
        p.setLevelName(levelNameFor(rs.getInt("clearance_level")));
        p.setLinkedWorkstation(rs.getString("linked_workstation"));
        p.setAuditMode(rs.getString("audit_mode"));
        p.setSandboxMandatory(rs.getBoolean("sandbox_mandatory"));
        p.setIsDefault(rs.getBoolean("is_default"));
        p.setScopeType(rs.getString("scope_type"));
        p.setTenantId(rs.getString("tenant_id"));
        p.setOrgId(rs.getString("org_id"));
        Timestamp ca = rs.getTimestamp("created_at");
        p.setCreatedAt(ca != null ? ca.getTime() : null);
        Timestamp ua = rs.getTimestamp("updated_at");
        p.setUpdatedAt(ua != null ? ua.getTime() : null);
        return p;
    };

    private final RowMapper<SecurityProfile> ROLE_PROFILE_MAPPER = (rs, rowNum) -> {
        SecurityProfile p = new SecurityProfile();
        p.setRoleId(rs.getString("role_id"));
        p.setClearanceLevel(rs.getInt("clearance_level"));
        p.setLevel(rs.getInt("clearance_level"));
        p.setLevelName(levelNameFor(rs.getInt("clearance_level")));
        p.setLinkedWorkstation(rs.getString("linked_workstation"));
        p.setAuditMode(rs.getString("audit_mode"));
        p.setSandboxMandatory(rs.getBoolean("sandbox_mandatory"));
        p.setScopeType(rs.getString("scope_type"));
        p.setTenantId(rs.getString("tenant_id"));
        p.setOrgId(rs.getString("org_id"));
        Timestamp ca = rs.getTimestamp("created_at");
        p.setCreatedAt(ca != null ? ca.getTime() : null);
        Timestamp ua = rs.getTimestamp("updated_at");
        p.setUpdatedAt(ua != null ? ua.getTime() : null);
        return p;
    };

    private static String levelNameFor(int level) {
        return level >= 0 && level < LEVEL_NAMES.length ? LEVEL_NAMES[level] : ("L" + level);
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
    // 全局配置模板 CRUD
    // ────────────────────────────────────────────────

    /**
     * 全局安全配置模板列表（支持关键字搜索、分页与作用域过滤）。
     * 返回用户级配置和角色级配置的合并结果。
     * 可选参数：?scopeType=TENANT&scopeId=xxx 按作用域过滤
     */
    @GetMapping("/profiles")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId) {
        try {
            // 合并用户级和角色级配置 — 如果指定了 scopeType，按作用域查询
            List<SecurityProfile> all;
            if (scopeType != null && !scopeType.isBlank()) {
                all = queryProfilesByScope(scopeType, scopeId);
            } else {
                all = new ArrayList<>();
                all.addAll(queryAllUserProfiles());
                all.addAll(queryAllRoleProfiles());
            }

            // 关键字过滤
            if (keyword != null && !keyword.isEmpty()) {
                String kw = keyword.toLowerCase();
                all = all.stream()
                        .filter(p -> (p.getUserId() != null && p.getUserId().toLowerCase().contains(kw))
                                || (p.getRoleId() != null && p.getRoleId().toLowerCase().contains(kw))
                                || (p.getAuditMode() != null && p.getAuditMode().toLowerCase().contains(kw)))
                        .collect(Collectors.toList());
            }

            // 分页
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

    /**
     * 获取单个安全配置模板。
     */
    @GetMapping("/profiles/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        try {
            SecurityProfile profile = queryUserProfile(id);
            if (profile == null) {
                return ApiResponse.notFound("安全配置不存在: " + id);
            }
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("查询安全配置失败, id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前激活的全局默认配置。
     */
    @GetMapping("/profiles/active")
    public ApiResponse<?> getActive() {
        try {
            SecurityProfile active = queryGlobalDefaultProfile();
            if (active == null) {
                return ApiResponse.notFound("未找到激活的安全配置");
            }
            return ApiResponse.success(active);
        } catch (Exception e) {
            log.error("查询激活的安全配置失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // 新增（全局模板）
    // ────────────────────────────────────────────────

    /**
     * 创建全局安全配置模板。
     */
    @PostMapping("/profiles")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> create(@RequestBody SecurityProfile profile) {
        try {
            String id = profile.getUserId();
            if (id == null || id.isBlank()) {
                id = UUID.randomUUID().toString().replace("-", "");
            }

            boolean isDefault = Boolean.TRUE.equals(profile.getIsDefault());

            // 如果设为默认，先取消其他默认
            if (isDefault) {
                jdbc.update("UPDATE td_user_security_profile SET is_default = FALSE WHERE is_default = TRUE");
            }

            String sql = """
                    INSERT INTO td_user_security_profile
                        (user_id, clearance_level, linked_workstation, audit_mode, sandbox_mandatory, is_default, scope_type, tenant_id, org_id, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                    ON CONFLICT (user_id) DO UPDATE SET
                        clearance_level = COALESCE(?, td_user_security_profile.clearance_level),
                        linked_workstation = COALESCE(?, td_user_security_profile.linked_workstation),
                        audit_mode = COALESCE(?, td_user_security_profile.audit_mode),
                        sandbox_mandatory = COALESCE(?, td_user_security_profile.sandbox_mandatory),
                        is_default = COALESCE(?, td_user_security_profile.is_default),
                        scope_type = COALESCE(?, td_user_security_profile.scope_type),
                        tenant_id = COALESCE(?, td_user_security_profile.tenant_id),
                        org_id = COALESCE(?, td_user_security_profile.org_id),
                        updated_at = NOW()
                    """;

            jdbc.update(sql,
                    id,
                    profile.getClearanceLevel(), profile.getLinkedWorkstation(),
                    profile.getAuditMode(), profile.getSandboxMandatory(), isDefault,
                    profile.getScopeType(), profile.getTenantId(), profile.getOrgId(),
                    // ON CONFLICT params
                    profile.getClearanceLevel(), profile.getLinkedWorkstation(),
                    profile.getAuditMode(), profile.getSandboxMandatory(), isDefault,
                    profile.getScopeType(), profile.getTenantId(), profile.getOrgId());

            log.info("安全配置模板创建成功, id={}", id);

            // 返回创建的配置
            SecurityProfile created = queryUserProfile(id);
            return ApiResponse.success(created != null ? created : profile);
        } catch (Exception e) {
            log.error("创建安全配置失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // 更新（全局模板）
    // ────────────────────────────────────────────────

    /**
     * 更新全局安全配置模板。
     */
    @PutMapping("/profiles/{id}")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody SecurityProfile profile) {
        try {
            // 检查是否存在
            SecurityProfile existing = queryUserProfile(id);
            if (existing == null) {
                // 尝试角色表
                existing = queryRoleProfile(id);
                if (existing == null) {
                    return ApiResponse.notFound("安全配置不存在: " + id);
                }
            }

            boolean isDefault = Boolean.TRUE.equals(profile.getIsDefault());
            if (isDefault) {
                jdbc.update("UPDATE td_user_security_profile SET is_default = FALSE WHERE is_default = TRUE");
            }

            String sql = """
                    UPDATE td_user_security_profile SET
                        clearance_level = COALESCE(?, clearance_level),
                        linked_workstation = COALESCE(?, linked_workstation),
                        audit_mode = COALESCE(?, audit_mode),
                        sandbox_mandatory = COALESCE(?, sandbox_mandatory),
                        is_default = COALESCE(?, is_default),
                        updated_at = NOW()
                    WHERE user_id = ?
                    """;

            int rows = jdbc.update(sql,
                    profile.getClearanceLevel(), profile.getLinkedWorkstation(),
                    profile.getAuditMode(), profile.getSandboxMandatory(), isDefault,
                    id);

            if (rows == 0) {
                return ApiResponse.notFound("安全配置更新失败: " + id);
            }

            log.info("安全配置模板更新成功, id={}", id);
            SecurityProfile updated = queryUserProfile(id);
            return ApiResponse.success(updated != null ? updated : profile);
        } catch (Exception e) {
            log.error("更新安全配置失败, id={}", id, e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // 删除
    // ────────────────────────────────────────────────

    /**
     * 删除安全配置模板（不允许删除全局默认配置）。
     */
    @DeleteMapping("/profiles/{id}")
    @RequirePermission(permission = "security:config:delete")
    public ApiResponse<?> delete(@PathVariable String id) {
        try {
            if ("_global_default_".equals(id)) {
                return ApiResponse.badRequest("不允许删除全局默认安全配置");
            }

            int rows = jdbc.update("DELETE FROM td_user_security_profile WHERE user_id = ?", id);
            if (rows == 0) {
                rows = jdbc.update("DELETE FROM td_role_security_profile WHERE role_id = ?", id);
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

    // ────────────────────────────────────────────────
    // 激活
    // ────────────────────────────────────────────────

    /**
     * 激活指定安全配置为全局默认。
     */
    @PostMapping("/profiles/{id}/activate")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> activate(@PathVariable String id) {
        try {
            // 取消所有默认
            jdbc.update("UPDATE td_user_security_profile SET is_default = FALSE WHERE is_default = TRUE");

            // 设置目标为默认
            int rows = jdbc.update(
                    "UPDATE td_user_security_profile SET is_default = TRUE, updated_at = NOW() WHERE user_id = ?", id);

            if (rows == 0) {
                return ApiResponse.notFound("安全配置不存在: " + id);
            }

            log.info("安全配置已激活, id={}", id);
            SecurityProfile profile = queryUserProfile(id);
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("激活安全配置失败, id={}", id, e);
            return ApiResponse.internalError("激活失败: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // 克隆
    // ────────────────────────────────────────────────

    /**
     * 克隆安全配置。
     */
    @PostMapping("/profiles/{id}/clone")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> clone(@PathVariable String id,
                                @RequestParam(required = false) String newName) {
        try {
            SecurityProfile source = queryUserProfile(id);
            if (source == null) {
                source = queryRoleProfile(id);
            }
            if (source == null) {
                return ApiResponse.notFound("源安全配置不存在: " + id);
            }

            String newId = UUID.randomUUID().toString().replace("-", "");

            String sql = """
                    INSERT INTO td_user_security_profile
                        (user_id, clearance_level, linked_workstation, audit_mode, sandbox_mandatory, is_default, scope_type, tenant_id, org_id, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, FALSE, ?, ?, ?, NOW(), NOW())
                    """;

            jdbc.update(sql, newId,
                    source.getClearanceLevel(), source.getLinkedWorkstation(),
                    source.getAuditMode(), source.getSandboxMandatory(),
                    source.getScopeType(), source.getTenantId(), source.getOrgId());

            log.info("安全配置克隆成功, sourceId={}, newId={}", id, newId);

            SecurityProfile cloned = queryUserProfile(newId);
            return ApiResponse.success(cloned);
        } catch (Exception e) {
            log.error("克隆安全配置失败, sourceId={}", id, e);
            return ApiResponse.internalError("克隆失败: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // 辅助查询方法
    // ────────────────────────────────────────────────

    private SecurityProfile queryUserProfile(String userId) {
        try {
            String tenantId = getCurrentTenantId();
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = "SELECT * FROM td_user_security_profile WHERE user_id = ? AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1";
                params = new Object[]{userId, tenantId};
            } else {
                sql = "SELECT * FROM td_user_security_profile WHERE user_id = ? LIMIT 1";
                params = new Object[]{userId};
            }
            List<SecurityProfile> list = jdbc.query(sql, USER_PROFILE_MAPPER, params);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("查询用户安全配置异常: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    private SecurityProfile queryRoleProfile(String roleId) {
        try {
            String tenantId = getCurrentTenantId();
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = "SELECT * FROM td_role_security_profile WHERE role_id = ? AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1";
                params = new Object[]{roleId, tenantId};
            } else {
                sql = "SELECT * FROM td_role_security_profile WHERE role_id = ? LIMIT 1";
                params = new Object[]{roleId};
            }
            List<SecurityProfile> list = jdbc.query(sql, ROLE_PROFILE_MAPPER, params);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("查询角色安全配置异常: roleId={}, {}", roleId, e.getMessage());
            return null;
        }
    }

    private SecurityProfile queryGlobalDefaultProfile() {
        try {
            String tenantId = getCurrentTenantId();
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = "SELECT * FROM td_user_security_profile WHERE is_default = TRUE AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1";
                params = new Object[]{tenantId};
            } else {
                sql = "SELECT * FROM td_user_security_profile WHERE is_default = TRUE LIMIT 1";
                params = new Object[]{};
            }
            List<SecurityProfile> list = jdbc.query(sql, USER_PROFILE_MAPPER, params);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("查询全局默认安全配置异常: {}", e.getMessage());
            return null;
        }
    }

    private List<SecurityProfile> queryAllUserProfiles() {
        try {
            String tenantId = getCurrentTenantId();
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = "SELECT * FROM td_user_security_profile WHERE tenant_id = ? OR tenant_id IS NULL ORDER BY updated_at DESC";
                params = new Object[]{tenantId};
            } else {
                sql = "SELECT * FROM td_user_security_profile ORDER BY updated_at DESC";
                params = new Object[]{};
            }
            return jdbc.query(sql, USER_PROFILE_MAPPER, params);
        } catch (Exception e) {
            log.warn("查询所有用户安全配置异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SecurityProfile> queryAllRoleProfiles() {
        try {
            String tenantId = getCurrentTenantId();
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = "SELECT * FROM td_role_security_profile WHERE tenant_id = ? OR tenant_id IS NULL ORDER BY clearance_level DESC";
                params = new Object[]{tenantId};
            } else {
                sql = "SELECT * FROM td_role_security_profile ORDER BY clearance_level DESC";
                params = new Object[]{};
            }
            return jdbc.query(sql, ROLE_PROFILE_MAPPER, params);
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
            String sql;
            Object[] params;
            switch (scopeType) {
                case "TENANT":
                    sql = "SELECT * FROM td_user_security_profile WHERE tenant_id = ? AND scope_type = 'TENANT' LIMIT 1";
                    params = new Object[]{scopeId};
                    break;
                case "ORG":
                    sql = "SELECT * FROM td_user_security_profile WHERE org_id = ? AND scope_type = 'ORG' LIMIT 1";
                    params = new Object[]{scopeId};
                    break;
                default:
                    log.debug("未知作用域类型: {}", scopeType);
                    return null;
            }
            List<SecurityProfile> list = jdbc.query(sql, USER_PROFILE_MAPPER, params);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("按作用域查询安全配置异常: scopeType={}, scopeId={}, {}", scopeType, scopeId, e.getMessage());
            return null;
        }
    }

    /**
     * 按作用域类型列出所有安全配置。
     */
    private List<SecurityProfile> queryProfilesByScope(String scopeType, String scopeId) {
        try {
            String sql;
            Object[] params;
            if (scopeId != null && !scopeId.isBlank()) {
                switch (scopeType.toUpperCase()) {
                    case "TENANT":
                        sql = "SELECT * FROM td_user_security_profile WHERE tenant_id = ? ORDER BY updated_at DESC";
                        // Also check role table
                        List<SecurityProfile> userProfiles = jdbc.query(sql, USER_PROFILE_MAPPER, scopeId);
                        String roleSql = "SELECT * FROM td_role_security_profile WHERE tenant_id = ? ORDER BY updated_at DESC";
                        List<SecurityProfile> roleProfiles = jdbc.query(roleSql, ROLE_PROFILE_MAPPER, scopeId);
                        List<SecurityProfile> all = new ArrayList<>();
                        all.addAll(userProfiles);
                        all.addAll(roleProfiles);
                        return all;
                    case "ORG":
                        sql = "SELECT * FROM td_user_security_profile WHERE org_id = ? ORDER BY updated_at DESC";
                        params = new Object[]{scopeId};
                        break;
                    default:
                        sql = "SELECT * FROM td_user_security_profile WHERE scope_type = ? ORDER BY updated_at DESC";
                        params = new Object[]{scopeType.toUpperCase()};
                }
            } else {
                sql = "SELECT * FROM td_user_security_profile WHERE scope_type = ? ORDER BY updated_at DESC";
                params = new Object[]{scopeType.toUpperCase()};
            }
            return jdbc.query(sql, USER_PROFILE_MAPPER, params);
        } catch (Exception e) {
            log.warn("按作用域列出安全配置异常: scopeType={}, scopeId={}, {}", scopeType, scopeId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
