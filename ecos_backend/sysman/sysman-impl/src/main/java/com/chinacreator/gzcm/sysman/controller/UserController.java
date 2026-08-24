package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;
import com.chinacreator.gzcm.common.annotation.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import com.chinacreator.gzcm.sysman.service.UserQueryService;

@RestController
@RequestMapping("/api/v1/system/users")
public class UserController {

        private final UserQueryService userQueryService;
private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final IUserService userService;

    public UserController(IUserService userService, UserQueryService userQueryService) {
        this.userService = userService;
        this.userQueryService = userQueryService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            StringBuilder sql = new StringBuilder(
                "SELECT u.\"USER_ID\" as \"userId\", u.\"USERNAME\" as username, u.\"REAL_NAME\" as \"realName\", " +
                "u.\"EMAIL\" as email, u.\"MOBILE_TEL1\" as phone, u.\"STATUS\" as status, " +
                "u.\"LOCKED\" as locked, u.\"LAST_LOGIN_TIME\" as \"lastLoginTime\", " +
                "u.\"CREATED_TIME\" as \"createdTime\", " +
                "o.\"ORG_ID\" as \"orgId\", o.\"ORG_NAME\" as \"orgName\" " +
                "FROM TD_USER u " +
                "LEFT JOIN td_user_organization uo ON u.\"USER_ID\" = uo.\"USER_ID\" AND uo.\"IS_PRIMARY\" = '1' " +
                "LEFT JOIN td_organization o ON uo.\"ORG_ID\" = o.\"ORG_ID\" " +
                "WHERE 1=1 ");
            List<Object> params = new ArrayList<>();
            if (keyword != null && !keyword.isEmpty()) {
                sql.append("AND (u.\"USERNAME\" ILIKE ? OR u.\"EMAIL\" ILIKE ?) ");
                String kw = "%" + keyword + "%";
                params.add(kw); params.add(kw);
            }
            if (status != null && !status.isEmpty()) {
                sql.append("AND u.\"STATUS\" = ? ");
                params.add(status);
            } else {
                sql.append("AND u.\"STATUS\" != 'DELETED' ");
            }
            sql.append("ORDER BY u.\"CREATED_TIME\" DESC ");
            
            // Count total
            String countSql = "SELECT COUNT(*) FROM (" + sql.toString() + ") t";
            int total = userQueryService.queryForObject(countSql, Integer.class, params.toArray());
            
            // Paginate
            int offset = (page - 1) * pageSize;
            sql.append("LIMIT ? OFFSET ?");
            params.add(pageSize); params.add(offset);
            
            List<Map<String, Object>> rows = userQueryService.queryForList(sql.toString(), params.toArray());
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("page", page);
            result.put("pageSize", pageSize);
            result.put("total", total);
            result.put("data", rows);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to list users", e);
            return ApiResponse.internalError("查询用户列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        try {
            UserAccount user = userService.getUser(id);
            if (user == null) return ApiResponse.notFound("用户不存在");
            return ApiResponse.success(toMap(user));
        } catch (Exception e) {
            log.error("查询用户详情失败", e);
            return ApiResponse.internalError("查询用户详情失败: " + e.getMessage());
        }
    }

    @PostMapping
    @RequirePermission(permission = "system:user:create")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            String username = (String) body.get("username");
            String password = (String) body.get("password");
            if (username == null || username.isEmpty()) return ApiResponse.badRequest("用户名不能为空");
            if (password == null || password.isEmpty()) return ApiResponse.badRequest("密码不能为空");

            // 密码长度策略校验（从 sys_config 读取 password_min_length，默认8）
            int minPasswordLength = 8;
            try {
                String configValue = userQueryService.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key = ? AND status = 'active'",
                    String.class, "password_min_length");
                if (configValue != null && !configValue.isEmpty()) {
                    minPasswordLength = Integer.parseInt(configValue);
                }
            } catch (Exception e) {
                log.debug("读取 password_min_length 配置失败，使用默认值8: {}", e.getMessage());
            }
            if (password.length() < minPasswordLength) {
                return ApiResponse.badRequest(
                    "密码长度不符合安全策略要求 (最小" + minPasswordLength + "位)");
            }

            // 配额检查：max_users（admin操作跳过）
            String tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null && !tenantId.isBlank()) {
                try {
                    Integer maxUsers = userQueryService.queryForObject(
                        "SELECT max_users FROM ecos_tenant WHERE id = ?", Integer.class, tenantId);
                    if (maxUsers != null && maxUsers > 0) {
                        Integer currentCount = userQueryService.queryForObject(
                            "SELECT COUNT(*) FROM TD_USER", Integer.class);
                        if (currentCount != null && currentCount >= maxUsers) {
                            return ApiResponse.error(400, "用户数已达租户配额上限");
                        }
                    }
                } catch (Exception e) {
                    log.warn("配额检查失败，放行: {}", e.getMessage());
                }
            }

            UserAccount user = new UserAccount();
            user.setUserId(UUID.randomUUID().toString().replace("-", ""));
            user.setUsername(username);
            user.setRealName((String) body.getOrDefault("realName", username));
            user.setEmail((String) body.get("email"));
            user.setPhone((String) body.get("phone"));
            user.setOrgId((String) body.get("orgId"));
            user.setStatus("ACTIVE");
            user.setLocked("0");

            UserAccount created = userService.createUser(user, password, "admin");
            return ApiResponse.success(toMap(created));
        } catch (Exception e) {
            log.error("创建用户失败", e);
            return ApiResponse.internalError("创建用户失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @RequirePermission(permission = "system:user:update")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            UserAccount existing = userService.getUser(id);
            if (existing == null) return ApiResponse.notFound("用户不存在");

            if (body.containsKey("realName")) existing.setRealName((String) body.get("realName"));
            if (body.containsKey("email")) existing.setEmail((String) body.get("email"));
            if (body.containsKey("phone")) existing.setPhone((String) body.get("phone"));
            if (body.containsKey("orgId")) existing.setOrgId((String) body.get("orgId"));

            // 不更新密码时清空，避免 updateUser 对已哈希的密码重复校验
            existing.setPassword(null);

            UserAccount updated = userService.updateUser(existing, "admin");
            return ApiResponse.success(toMap(updated));
        } catch (Exception e) {
            log.error("更新用户失败", e);
            return ApiResponse.internalError("更新用户失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permission = "system:user:delete")
    public ApiResponse<Void> delete(@PathVariable String id) {
        try {
            userService.deleteUser(id, "admin");
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除用户失败", e);
            return ApiResponse.internalError("删除用户失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/password")
    @RequirePermission(permission = "system:user:update")
    public ApiResponse<Void> resetPassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            String newPassword = body.get("password");
            if (newPassword == null || newPassword.isEmpty()) return ApiResponse.badRequest("密码不能为空");
            userService.resetPassword(id, newPassword, "admin");
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("重置密码失败", e);
            return ApiResponse.internalError("重置密码失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/status")
    @RequirePermission(permission = "system:user:update")
    public ApiResponse<Void> toggleStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            String action = body.get("action");
            if ("activate".equals(action)) userService.activateUser(id, "admin");
            else if ("deactivate".equals(action)) userService.deactivateUser(id, "admin");
            else if ("lock".equals(action)) userService.lockUser(id, "admin");
            else if ("unlock".equals(action)) userService.unlockUser(id, "admin");
            else return ApiResponse.badRequest("无效操作: " + action);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("修改用户状态失败", e);
            return ApiResponse.internalError("修改用户状态失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/roles")
    @RequirePermission(permission = "system:user:query")
    public ApiResponse<List<Map<String, Object>>> getRoles(@PathVariable String id) {
        try {
            String sql = "SELECT r.\"ROLE_ID\" as \"roleId\", r.\"ROLE_NAME\" as \"roleName\", r.\"ROLE_CODE\" as \"roleCode\" FROM TD_ROLE r " +
                         "INNER JOIN TD_USER_ROLE ur ON r.\"ROLE_ID\" = ur.\"ROLE_ID\" WHERE ur.\"USER_ID\" = ?";
            List<Map<String, Object>> roles = userQueryService.queryForList(sql, id);
            return ApiResponse.success(roles);
        } catch (Exception e) {
            log.error("查询用户角色失败", e);
            return ApiResponse.internalError("查询用户角色失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/roles")
    @RequirePermission(permission = "system:user:update")
    public ApiResponse<Void> assignRoles(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> roleIds = (List<String>) body.get("roleIds");
            if (roleIds == null) {
                return ApiResponse.badRequest("roleIds 不能为空");
            }

            userQueryService.update("DELETE FROM TD_USER_ROLE WHERE \"USER_ID\" = ?", id);
            for (String roleId : roleIds) {
                userQueryService.update("INSERT INTO TD_USER_ROLE (\"USER_ID\", \"ROLE_ID\") VALUES (?, ?)", id, roleId);
            }
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("分配用户角色失败", e);
            return ApiResponse.internalError("分配用户角色失败: " + e.getMessage());
        }
    }

    private Map<String, Object> toMap(UserAccount u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getUserId());
        m.put("username", u.getUsername());
        m.put("realName", u.getRealName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("orgId", u.getOrgId());
        m.put("status", u.getStatus());
        m.put("locked", u.getLocked());
        m.put("lastLoginTime", u.getLastLoginTime());
        m.put("createdTime", u.getCreatedTime());
        return m;
    }
}
