/**
 * 用户控制器 — 薄增强版（通过 {@link com.chinacreator.gzcm.sysman.iam.service.IUserService}
 * 编排系统能力，不直接访问 SecurityManager）。
 *
 * <p>路由前缀: /api/v1/system/users
 *
 * <h3>覆盖的四类接口</h3>
 * <ul>
 *   <li>标准 CRUD：列表 / 详情 / 创建 / 更新 / 删除；</li>
 *   <li>扩展能力：重置密码、状态切换、角色查询与绑定；</li>
 *   <li>自读面：{@code /me/permission-decisions}、{@code /me/profile}（不加权限注解）；</li>
 *   <li>P0 运维：强制下线、一次性随机密码、批量创建。</li>
 * </ul>
 *
 * <h3>安全</h3>
 * <ul>
 *   <li>密码存 BCrypt，不可逆；</li>
 *   <li>列表/详情响应不暴露 passwordHash 字段。</li>
 * </ul>
 *
 * <p><b>#147CI（DoD DI-2 第十三批）</b>：出入参强类型化——列表 SQL 收回
 * {@code UserQueryService#listUsers}（行面 record 11 键，时间列仍为 JDBC Timestamp 由同一
 * ObjectMapper 渲染）、角色查询收回 {@code listUserRoles}；详情/创建/更新改 {@link UserVO}；
 * 创建/更新/角色绑定请求改 DTO（更新用 {@code xxxPresent} 保留旧 {@code containsKey}
 * 「显式 null 写空」语义）；强制下线/随机密码/批量创建回执改 VO（批量 {@code errors}
 * 仍只在非空时出现）。</p>
 */
package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.common.exception.UnauthorizedException;
import com.chinacreator.gzcm.sysman.dto.UserBatchSaveDTO;
import com.chinacreator.gzcm.sysman.dto.UserSaveDTO;
import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;
import com.chinacreator.gzcm.sysman.iam.cache.PermissionCacheService;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;
import com.chinacreator.gzcm.sysman.service.UserQueryService;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/system/users")
public class UserController {

	private final UserQueryService userQueryService;
	private static final Logger log = LoggerFactory.getLogger(UserController.class);
	private final IUserService userService;
	private final PermissionCacheService permissionCacheService;
	private final com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService permissionCheckService;

	public UserController(IUserService userService, UserQueryService userQueryService,
			PermissionCacheService permissionCacheService,
			com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService permissionCheckService) {
		this.userService = userService;
		this.userQueryService = userQueryService;
		this.permissionCacheService = permissionCacheService;
		this.permissionCheckService = permissionCheckService;
	}

	@GetMapping
	public ApiResponse<UserQueryService.UserPage> list(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		try {
			return ApiResponse.success(userQueryService.listUsers(keyword, status, page, pageSize));
		} catch (Exception e) {
			log.error("Failed to list users", e);
			return ApiResponse.internalError("查询用户列表失败（详情见服务端日志）");
		}
	}

	/**
	 * 当前登录用户的<b>有效权限决策</b>（PEP 读面；§2.9.8③ 缓存消费点 · #147AF/D）。
	 *
	 * <p>为什么不加 {@code @RequirePermission}：这是"我看我自己"的自读面——任何已认证用户都应能
	 * 读到自己的有效授权，否则前端无法在无超管权限时解释"为什么这个按钮不可见"。读取走
	 * {@code IPermissionCheckService#getUserEffectiveDecisions}，即权限缓存的真实读路径
	 * （命中即回放、未命中现算回写）；<b>不改变任何鉴权结论</b>（缓存不是授权权威）。</p>
	 */
	@GetMapping("/me/permission-decisions")
	public ApiResponse<Map<String, String>> myPermissionDecisions() {
		try {
			var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
			String userId = auth == null ? null : auth.getName();
			if (userId == null || userId.isBlank()) {
				throw new UnauthorizedException("未登录或Token已过期");
			}
			return ApiResponse.success(permissionCheckService.getUserEffectiveDecisions(userId));
		} catch (Exception e) {
			log.error("读取有效权限决策失败", e);
			return ApiResponse.internalError("读取有效权限决策失败（详情见服务端日志）");
		}
	}

	/**
	 * 当前登录用户的<b>基础信息投影</b>（14 类矩阵「用户基础信息」读面；§2.9.8⑥；#147AF/D）。
	 *
	 * <p>只返回白名单字段（userId/username/realName/orgId/status/locked/userType），
	 * 口令、身份证、手机、邮箱、地址、证书序列一律不出现在响应与缓存中。自读面不加权限注解。</p>
	 */
	@GetMapping("/me/profile")
	public ApiResponse<com.chinacreator.gzcm.sysman.iam.service.IUserService.UserBasicProfile> myProfile() {
		try {
			var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
			String userId = auth == null ? null : auth.getName();
			if (userId == null || userId.isBlank()) {
				throw new UnauthorizedException("未登录或Token已过期");
			}
			var profile = userService.getUserBasicProfile(userId);
			return profile == null ? ApiResponse.notFound("用户不存在") : ApiResponse.success(profile);
		} catch (Exception e) {
			log.error("读取用户基础信息失败", e);
			return ApiResponse.internalError("读取用户基础信息失败（详情见服务端日志）");
		}
	}

	@GetMapping("/{id}")
	public ApiResponse<UserVO> get(@PathVariable String id) {
		try {
			UserAccount user = userService.getUser(id);
			if (user == null) return ApiResponse.notFound("用户不存在");
			return ApiResponse.success(toVO(user));
		} catch (Exception e) {
			log.error("查询用户详情失败", e);
			return ApiResponse.internalError("查询用户详情失败（详情见服务端日志）");
		}
	}

	@PostMapping
	@RequirePermission(permission = "system:user:create")
	public ApiResponse<UserVO> create(@RequestBody UserCreateRequest body) {
		try {
			String username = body.getUsername();
			String password = body.getPassword();
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
			user.setRealName(body.getRealName() != null ? body.getRealName() : username);
			user.setEmail(body.getEmail());
			user.setPhone(body.getPhone());
			user.setOrgId(body.getOrgId());
			user.setStatus("ACTIVE");
			user.setLocked("0");

			UserAccount created = userService.createUser(user, password, "admin");
			return ApiResponse.success(toVO(created));
		} catch (Exception e) {
			log.error("创建用户失败", e);
			return ApiResponse.internalError("创建用户失败（详情见服务端日志）");
		}
	}

	@PutMapping("/{id}")
	@RequirePermission(permission = "system:user:update")
	public ApiResponse<UserVO> update(@PathVariable String id, @RequestBody UserUpdateRequest body) {
		try {
			UserAccount existing = userService.getUser(id);
			if (existing == null) return ApiResponse.notFound("用户不存在");

			if (body.isRealNamePresent()) existing.setRealName(body.getRealName());
			if (body.isEmailPresent()) existing.setEmail(body.getEmail());
			if (body.isPhonePresent()) existing.setPhone(body.getPhone());
			if (body.isOrgIdPresent()) existing.setOrgId(body.getOrgId());

			// 不更新密码时清空，避免 updateUser 对已哈希的密码重复校验
			existing.setPassword(null);

			UserAccount updated = userService.updateUser(existing, "admin");
			return ApiResponse.success(toVO(updated));
		} catch (Exception e) {
			log.error("更新用户失败", e);
			return ApiResponse.internalError("更新用户失败（详情见服务端日志）");
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
			return ApiResponse.internalError("删除用户失败（详情见服务端日志）");
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
			return ApiResponse.internalError("重置密码失败（详情见服务端日志）");
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
			return ApiResponse.internalError("修改用户状态失败（详情见服务端日志）");
		}
	}

	@GetMapping("/{id}/roles")
	@RequirePermission(permission = "system:user:query")
	public ApiResponse<List<UserQueryService.UserRoleRow>> getRoles(@PathVariable String id) {
		try {
			return ApiResponse.success(userQueryService.listUserRoles(id));
		} catch (Exception e) {
			log.error("查询用户角色失败", e);
			return ApiResponse.internalError("查询用户角色失败（详情见服务端日志）");
		}
	}

	@PutMapping("/{id}/roles")
	@RequirePermission(permission = "system:user:update")
	public ApiResponse<Void> assignRoles(@PathVariable String id, @RequestBody RoleIdsRequest body) {
		try {
			List<String> roleIds = body.getRoleIds();
			if (roleIds == null) {
				return ApiResponse.badRequest("roleIds 不能为空");
			}

			userQueryService.update("DELETE FROM TD_USER_ROLE WHERE user_id = ?", id);
			for (String roleId : roleIds) {
				userQueryService.update("INSERT INTO TD_USER_ROLE (user_id, role_id, created_by) VALUES (?, ?, 'admin')", id, roleId);
			}
			// 授权关系是权限缓存的权威数据源：提交后必须立即失效该用户的全部缓存视图，
			// 否则撤权要等 TTL 才生效（§2.9.8④「先提交权威数据、再失效缓存」+ ⑥「证明撤权生效」；#147AA）。
			permissionCacheService.evictUser(id);
			return ApiResponse.success();
		} catch (Exception e) {
			log.error("分配用户角色失败", e);
			return ApiResponse.internalError("分配用户角色失败（详情见服务端日志）");
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
	public ApiResponse<ForceLogoutResultVO> forceLogout(@PathVariable String id) {
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
			return ApiResponse.success(new ForceLogoutResultVO(id, sessionsCleared,
					"强制下线已执行，该用户需重新登录"));
		} catch (Exception e) {
			log.error("[force-logout] 失败 id={}", id, e);
			return ApiResponse.internalError("强制下线失败（详情见服务端日志）");
		}
	}

	/**
	 * POST /api/v1/system/users/{id}/reset-password
	 * 生成 12 位随机密码，写库，响应中一次性返回明文（不落库，不落日志）。
	 */
	@PostMapping("/{id}/reset-password")
	@RequirePermission(permission = "system:user:manage")
	public ApiResponse<ResetPasswordResultVO> resetPasswordT1(@PathVariable String id) {
		try {
			log.info("[reset-password] userId={}", id);
			String tempPwd = genTempPassword();
			userService.resetPassword(id, tempPwd, "admin");
			return ApiResponse.success(new ResetPasswordResultVO(id, tempPwd,
					"密码已重置，请立即通知用户使用新密码"));
		} catch (Exception e) {
			log.error("[reset-password] 失败 id={}", id, e);
			return ApiResponse.internalError("重置密码失败（详情见服务端日志）");
		}
	}

	/**
	 * POST /api/v1/system/users/batch
	 * 批量创建用户，上限 100 条。password 可为空，缺省生成随机密码。
	 */
	@PostMapping("/batch")
	@RequirePermission(permission = "system:user:manage")
	public ApiResponse<BatchCreateResultVO> batchCreate(@RequestBody UserBatchSaveDTO dto) {
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
					errors.add(tag + " (" + u.getUsername() + "): 创建失败（详情见服务端日志）");
						log.warn("[batch-create] 单行创建失败 tag={} user={}", tag, u.getUsername(), e);
					failed++;
				}
			}
			return ApiResponse.success(new BatchCreateResultVO(created, failed,
					errors.isEmpty() ? null : errors));
		} catch (Exception e) {
			log.error("[batch-create] 失败", e);
			return ApiResponse.internalError("批量创建失败（详情见服务端日志）");
		}
	}

	private static String genTempPassword() {
		StringBuilder sb = new StringBuilder(12);
		for (int i = 0; i < 12; i++) sb.append(PWD_CHARSET.charAt(CRYPTO.nextInt(PWD_CHARSET.length())));
		return sb.toString();
	}

	static UserVO toVO(UserAccount u) {
		return new UserVO(u.getUserId(), u.getUsername(), u.getRealName(), u.getEmail(), u.getPhone(),
				u.getOrgId(), u.getStatus(), u.getLocked(), u.getLastLoginTime(), u.getCreatedTime());
	}

	// ── 请求 DTO / 响应 VO（#147CI） ─────────────────────────────

	/** 建用户请求（realName 缺省回退 username，与旧 getOrDefault 一致）。 */
	public static final class UserCreateRequest {
		private String username;
		private String password;
		private String realName;
		private String email;
		private String phone;
		private String orgId;

		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

		public String getRealName() {
			return realName;
		}

		public void setRealName(String realName) {
			this.realName = realName;
		}

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}

		public String getPhone() {
			return phone;
		}

		public void setPhone(String phone) {
			this.phone = phone;
		}

		public String getOrgId() {
			return orgId;
		}

		public void setOrgId(String orgId) {
			this.orgId = orgId;
		}
	}

	/** 改用户请求（{@code xxxPresent} 保留旧 containsKey「显式 null 写空」语义）。 */
	public static final class UserUpdateRequest {
		private String realName;
		private String email;
		private String phone;
		private String orgId;
		private boolean realNamePresent;
		private boolean emailPresent;
		private boolean phonePresent;
		private boolean orgIdPresent;

		public String getRealName() {
			return realName;
		}

		public void setRealName(String realName) {
			this.realName = realName;
			this.realNamePresent = true;
		}

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
			this.emailPresent = true;
		}

		public String getPhone() {
			return phone;
		}

		public void setPhone(String phone) {
			this.phone = phone;
			this.phonePresent = true;
		}

		public String getOrgId() {
			return orgId;
		}

		public void setOrgId(String orgId) {
			this.orgId = orgId;
			this.orgIdPresent = true;
		}

		public boolean isRealNamePresent() {
			return realNamePresent;
		}

		public boolean isEmailPresent() {
			return emailPresent;
		}

		public boolean isPhonePresent() {
			return phonePresent;
		}

		public boolean isOrgIdPresent() {
			return orgIdPresent;
		}
	}

	/** 角色绑定请求。 */
	public static final class RoleIdsRequest {
		private List<String> roleIds;

		public List<String> getRoleIds() {
			return roleIds;
		}

		public void setRoleIds(List<String> roleIds) {
			this.roleIds = roleIds;
		}
	}

	/** 用户视图（10 键，键序与旧 toMap 一致；口令/哈希永不出现）。 */
	public record UserVO(String userId, String username, String realName, String email, String phone,
						 String orgId, String status, String locked, LocalDateTime lastLoginTime,
						 LocalDateTime createdTime) {
	}

	/** 强制下线回执（3 键）。 */
	public record ForceLogoutResultVO(String userId, int sessionsCleared, String msg) {
	}

	/** 随机密码回执（3 键；明文一次性返回，不落库/日志）。 */
	public record ResetPasswordResultVO(String userId, String tempPassword, String msg) {
	}

	/** 批量创建回执（errors 仅非空时出现）。 */
	public record BatchCreateResultVO(int created, int failed,
									  @JsonInclude(JsonInclude.Include.NON_NULL) List<String> errors) {
	}
}
