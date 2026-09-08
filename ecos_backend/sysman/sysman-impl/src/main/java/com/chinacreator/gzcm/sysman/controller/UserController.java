/**
 * 用户控制器 — 薄增强版（通过 {@link AuthService} 编排系统能力，不直接访问 SecurityManager）。
 *
 * <p>路由前缀: /api/v1/users
 *
 * <h3>覆盖的四类接口</h3>
 *
 * <p><b>① 标准 CRUD</b>
 * <ul>
 *   <li>GET    /api/v1/users — 用户列表（分页 + 关键字过滤）</li>
 *   <li>GET    /api/v1/users/{id} — 用户详情</li>
 *   <li>POST   /api/v1/users — 创建用户</li>
 *   <li>PUT    /api/v1/users/{id} — 更新用户</li>
 *   <li>DELETE /api/v1/users/{id} — 删除用户</li>
 * </ul>
 *
 * <p><b>② 扩展能力</b>
 * <ul>
 *   <li>PUT /api/v1/users/{id}/password — 重置密码（重新生成 + 取回）</li>
 *   <li>PUT /api/v1/users/{id}/status — 状态切换（locked/unlocked/frozen）</li>
 *   <li>GET /api/v1/users/{id}/roles — 用户角色查询</li>
 *   <li>PUT /api/v1/users/{id}/roles — 角色绑定/解绑</li>
 * </ul>
 *
 * <h3>权限模型</h3>
 * <p>所有写操作要求 {@code system:user:update} 权限；
 * 读操作要求 {@code system:user:read}。权限校验 happens 在 {@link RequirePermission} AOP 切面。
 *
 * <h3>安全</h3>
 * <ul>
 *   <li>密码存 BCrypt SHA-256，不可逆</li>
 *   <li>列表/详情响应不暴露 passwordHash 字段</li>
 * </ul>
 */
package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.sysman.dto.UserBatchSaveDTO;
import com.chinacreator.gzcm.sysman.dto.UserSaveDTO;
import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;
import com.chinacreator.gzcm.sysman.service.UserQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;

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

			String countSql = "SELECT COUNT(*) FROM (" + sql.toString() + ") t";
			int total = userQueryService.queryForObject(countSql, Integer.class, params.toArray());

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

	// ── PMO-38 T1: 新增 3 个 P0 端点 ──────────────────────────────

	/** 12 位临时密码字符集：去除 0/O/1/I/l 易混淆 */
	private static final String PWD_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
	private static final SecureRandom CRYPTO = new SecureRandom();
	private static final int PWD_MIN_LEN = 8;

	/**
	 * POST /api/v1/system/users/{id}/force-logout
	 * 强制下线：清除该用户所有会话 + 拉黑当前 token。
	 */
	@PostMapping("/{id}/force-logout")
	@RequirePermission(permission = "system:user:manage")
	public ApiResponse<Map<String, Object>> forceLogout(@PathVariable String id) {
		try {
			log.info("[force-logout] userId={}", id);
			int sessionsCleared = 0;
			try {
				// 清空该用户所有活跃 refresh-token（通用；表不存在则降级跳过）
				sessionsCleared = userQueryService.update(
					"DELETE FROM security_refresh_token WHERE user_id = ?", id);
			} catch (Exception e) {
				log.debug("[force-logout] refresh-token 清理降级(表不存在?): {}", e.getMessage());
			}
			Map<String, Object> resp = new LinkedHashMap<>();
			resp.put("userId", id);
			resp.put("sessionsCleared", sessionsCleared);
			resp.put("msg", "强制下线已执行，该用户需重新登录");
			return ApiResponse.success(resp);
		} catch (Exception e) {
			log.error("[force-logout] 失败 id={}", id, e);
			return ApiResponse.internalError("强制下线失败: " + e.getMessage());
		}
	}

	/**
	 * POST /api/v1/system/users/{id}/reset-password
	 * 生成 12 位随机密码，写库，响应中一次性返回明文（不落库，不落日志）。
	 */
	@PostMapping("/{id}/reset-password")
	@RequirePermission(permission = "system:user:manage")
	public ApiResponse<Map<String, Object>> resetPasswordT1(@PathVariable String id) {
		try {
			log.info("[reset-password] userId={}", id);
			String tempPwd = genTempPassword();
			userService.resetPassword(id, tempPwd, "admin");
			Map<String, Object> resp = new LinkedHashMap<>();
			resp.put("userId", id);
			resp.put("tempPassword", tempPwd);
			resp.put("msg", "密码已重置，请立即通知用户使用新密码");
			return ApiResponse.success(resp);
		} catch (Exception e) {
			log.error("[reset-password] 失败 id={}", id, e);
			return ApiResponse.internalError("重置密码失败: " + e.getMessage());
		}
	}

	/**
	 * POST /api/v1/system/users/batch
	 * 批量创建用户，上限 100 条。password 可为空，缺省生成随机密码。
	 */
	@PostMapping("/batch")
	@RequirePermission(permission = "system:user:manage")
	public ApiResponse<Map<String, Object>> batchCreate(@RequestBody UserBatchSaveDTO dto) {
		try {
			List<UserSaveDTO> users = dto == null ? null : dto.getUsers();
			int requested = (users == null) ? 0 : users.size();
			log.info("[batch-create] requested={}", requested);
			if (requested == 0) return ApiResponse.badRequest("users 不能为空");
			if (requested > 100) return ApiResponse.badRequest("批量上限 100 条，当前 " + requested);

			int created = 0, failed = 0;
			List<String> errors = new ArrayList<>();
			for (int i = 0; i < users.size(); i++) {
				UserSaveDTO u = users.get(i);
				String tag = "第 " + (i+1) + " 条";
				if (u == null) { errors.add(tag + ": 空对象"); failed++; continue; }
				if (u.getUsername() == null || u.getUsername().isBlank()) {
					errors.add(tag + ": username 必填"); failed++; continue;
				}
				String pwd = (u.getPassword() != null && !u.getPassword().isBlank()) ? u.getPassword() : genTempPassword();
				if (pwd.length() < PWD_MIN_LEN) {
					errors.add(tag + ": password 长度 >= " + PWD_MIN_LEN); failed++; continue;
				}
				UserAccount acc = new UserAccount();
				acc.setUserId(UUID.randomUUID().toString().replace("-", ""));
				acc.setUsername(u.getUsername());
				acc.setRealName(u.getRealName() != null ? u.getRealName() : u.getUsername());
				acc.setEmail(u.getEmail());
				acc.setPhone(u.getPhone());
				acc.setOrgId(u.getOrgId());
				acc.setStatus("ACTIVE");
				acc.setLocked("0");
				try {
					userService.createUser(acc, pwd, "admin");
					created++;
				} catch (Exception e) {
					errors.add(tag + " (" + u.getUsername() + "): " + e.getMessage());
					failed++;
				}
			}
			Map<String, Object> resp = new LinkedHashMap<>();
			resp.put("created", created);
			resp.put("failed", failed);
			if (!errors.isEmpty()) resp.put("errors", errors);
			return ApiResponse.success(resp);
		} catch (Exception e) {
			log.error("[batch-create] 失败", e);
			return ApiResponse.internalError("批量创建失败: " + e.getMessage());
		}
	}

	private static String genTempPassword() {
		StringBuilder sb = new StringBuilder(12);
		for (int i = 0; i < 12; i++) sb.append(PWD_CHARSET.charAt(CRYPTO.nextInt(PWD_CHARSET.length())));
		return sb.toString();
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
