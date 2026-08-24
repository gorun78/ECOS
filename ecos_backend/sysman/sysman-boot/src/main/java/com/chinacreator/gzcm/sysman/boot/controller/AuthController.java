package com.chinacreator.gzcm.sysman.boot.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.chinacreator.gzcm.sysman.boot.service.AuthService;
import com.chinacreator.gzcm.sysman.dto.ChangePasswordRequest;
import com.chinacreator.gzcm.sysman.dto.LoginRequest;
import com.chinacreator.gzcm.sysman.dto.LoginResponse;
import com.chinacreator.gzcm.sysman.dto.RefreshTokenRequest;
import com.chinacreator.gzcm.sysman.dto.TokenRefreshResponse;
import com.chinacreator.gzcm.sysman.dto.UserInfoResponse;
import com.chinacreator.gzcm.sysman.security.JwtTokenProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证控制器 — 登录 / 获取用户信息 / 刷新 Token / 修改密码。
 * <p>
 * 数据库用户表验证 + BCrypt 密码比对 + 登录锁定 + 密码强度校验。
 */
@RestController
@RequestMapping({"/api/v1/auth", "/auth"})
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthService authService;
    private final SysConfigService sysConfigService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 内存中的 Refresh Token 存储（生产环境应使用 Redis） */
    private static final Map<String, String> refreshTokens = new ConcurrentHashMap<>();

    public AuthController(JwtTokenProvider jwtTokenProvider,
                          AuthService authService,
                          SysConfigService sysConfigService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authService = authService;
        this.sysConfigService = sysConfigService;
    }

    // ── 配置读取辅助方法 ──────────────────────────────────

    private int getIntConfig(String key, int defaultValue) {
        return sysConfigService.getInt(key, defaultValue);
    }

    private boolean getBoolConfig(String key, boolean defaultValue) {
        return sysConfigService.getBoolean(key, defaultValue);
    }

    // ── 登录 ──────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest) {
        String username = request.username();
        String password = request.password();

        // 查询用户（含安全字段）
        List<Map<String, Object>> users = authService.findUserForLogin(username);

        if (users.isEmpty()) {
            log.warn("Login failed — user not found: {}", username);
            return ResponseEntity.status(401).body(ApiResponse.unauthorized("用户名或密码错误"));
        }

        Map<String, Object> user = users.get(0);
        String passwordHash = (String) user.get("password_hash");

        // ── T1-1: 检查账户是否被锁定 ──
        Timestamp lockedUntil = (Timestamp) user.get("locked_until");
        if (lockedUntil != null && lockedUntil.toInstant().isAfter(Instant.now())) {
            log.warn("Login blocked — account locked until {}: {}", lockedUntil, username);
            // 使用 423 Locked 状态码
            return ResponseEntity.status(423)
                    .body(ApiResponse.error(423, "ACCOUNT_LOCKED", "账户已锁定，请稍后再试"));
        }

        // ── 密码比对 ──
        if (!passwordEncoder.matches(password, passwordHash)) {
            // ── T1-2: 密码错误 — 增加失败计数 ──
            int failedAttempts = user.get("failed_attempts") != null
                    ? ((Number) user.get("failed_attempts")).intValue() : 0;
            failedAttempts++;

            int maxLoginAttempts = getIntConfig("max_login_attempts", 5);
            int lockoutDurationMinutes = getIntConfig("lockout_duration_minutes", 15);
            int remainingAttempts = maxLoginAttempts - failedAttempts;

            if (failedAttempts >= maxLoginAttempts) {
                // 锁定账户
                authService.lockAccount(failedAttempts, lockoutDurationMinutes, username);
                log.warn("Account locked — {} failed attempts for user: {}", failedAttempts, username);
                return ResponseEntity.status(423)
                        .body(ApiResponse.error(423, "ACCOUNT_LOCKED",
                                "账户已锁定" + lockoutDurationMinutes + "分钟，请稍后再试"));
            } else {
                authService.updateFailedAttempts(failedAttempts, username);
                log.warn("Login failed — wrong password for user: {} (attempt {}/{})",
                        username, failedAttempts, maxLoginAttempts);
                return ResponseEntity.status(401)
                        .body(ApiResponse.unauthorized(
                                "用户名或密码错误（剩余尝试" + remainingAttempts + "次）"));
            }
        }

        // ── R1.1: 工作站登录校验（密码正确后、签发 token 前） ──
        String userId = (String) user.get("id");
        String linkedWorkstation = authService.findLinkedWorkstation(userId);
        if (linkedWorkstation != null && !linkedWorkstation.isBlank()) {
            String clientIp = extractClientIp(httpRequest);
            if (!workstationMatches(linkedWorkstation, clientIp)) {
                log.warn("R1.1 工作站校验失败 — user={}, linkedWs={}, clientIp={}",
                        username, linkedWorkstation, clientIp);
                // 写审计日志到 ecos_audit_log（action=workstation_mismatch, result=FAILURE）
                authService.writeWorkstationMismatchAudit(username, userId, linkedWorkstation, clientIp);
                return ResponseEntity.status(403)
                        .body(ApiResponse.forbidden("工作站校验失败：当前IP未绑定到该账户"));
            }
            log.debug("R1.1 工作站校验通过 — user={}, clientIp={}", username, clientIp);
        }

        // ── 密码正确 ──

        // ── T1-3a: 检查是否需要强制修改密码 ──
        Boolean passwordChangeRequired = (Boolean) user.get("password_change_required");
        if (Boolean.TRUE.equals(passwordChangeRequired)) {
            String changeToken = jwtTokenProvider.createChangeToken(userId);
            // 重置失败计数（密码正确但需换密）
            authService.resetFailedAttempts(userId);
            log.info("User {} (id={}) password change required, changeToken issued", username, userId);
            Map<String, String> data = Map.of("changeToken", changeToken,
                    "message", "首次登录需修改密码");
            return ResponseEntity.status(200)
                    .body(ApiResponse.success("PASSWORD_CHANGE_REQUIRED", data));
        }

        // ── T1-3b: 检查密码是否过期 ──
        int passwordExpireDays = getIntConfig("password_expire_days", 90);
        if (passwordExpireDays > 0) {
            Timestamp lastPasswordChange = (Timestamp) user.get("last_password_change");
            if (lastPasswordChange != null) {
                LocalDateTime expireTime = lastPasswordChange.toLocalDateTime().plusDays(passwordExpireDays);
                if (LocalDateTime.now().isAfter(expireTime)) {
                    log.warn("User {} (id={}) password expired (last change: {})",
                            username, userId, lastPasswordChange);
                    // 颁发 changeToken 让用户能修改密码
                    String changeToken = jwtTokenProvider.createChangeToken(userId);
                    authService.resetFailedAttempts(userId);
                    Map<String, String> data = Map.of("changeToken", changeToken,
                            "message", "密码已过期，请修改密码");
                    return ResponseEntity.status(200)
                            .body(ApiResponse.success("PASSWORD_EXPIRED", data));
                }
            }
        }

        // ── 正常登录：重置失败计数 ──
        authService.resetFailedAttempts(userId);

        // 提取用户信息
        List<String> roles = parseRoles((String) user.get("roles"));

        // 查询租户ID
        Map<String, Object> extraClaims = new java.util.HashMap<>();
        try {
            List<Map<String, Object>> tenantRows = authService.findTenantByUsername(username);
            if (tenantRows != null && !tenantRows.isEmpty()) {
                Object tid = tenantRows.get(0).get("TENANT_ID");
                if (tid != null && !tid.toString().isBlank()) {
                    extraClaims.put("tenant_id", tid.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to query tenant for {}: {}", username, e.getMessage());
        }
        if (!extraClaims.containsKey("tenant_id")) {
            extraClaims.put("tenant_id", "tenant-a");
        }

        // 签发 JWT
        String accessToken = jwtTokenProvider.createAccessToken(userId, roles, extraClaims);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        // 存储 Refresh Token（以便吊销）
        refreshTokens.put(refreshToken, userId);

        log.info("User {} (id={}) logged in, access token issued", username, userId);

        LoginResponse data = new LoginResponse(accessToken, refreshToken, username, userId, roles);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ── 获取当前用户信息 ──────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoResponse>> me(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(ApiResponse.unauthorized("未登录"));
        }

        try {
            String token = auth.substring(7);
            Claims claims = jwtTokenProvider.validateToken(token);

            // 验证是 access token
            if (!"access".equals(claims.get("type"))) {
                return ResponseEntity.status(401).body(ApiResponse.unauthorized("Token类型无效"));
            }

            String userId = claims.getSubject();

            // 从数据库查询用户信息
            List<Map<String, Object>> users = authService.findUserById(userId);

            String username;
            List<String> roles;
            if (!users.isEmpty()) {
                Map<String, Object> user = users.get(0);
                username = (String) user.get("username");
                roles = parseRoles((String) user.get("roles"));
            } else {
                // fallback: 使用 token 中的信息
                username = userId;
                @SuppressWarnings("unchecked")
                List<String> tokenRoles = claims.get("roles", List.class);
                roles = tokenRoles != null ? tokenRoles : Collections.emptyList();
            }

            UserInfoResponse data = new UserInfoResponse(username, userId, roles);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (JwtException | IllegalArgumentException e) {
            return ResponseEntity.status(401).body(ApiResponse.unauthorized("Token无效或已过期"));
        }
    }

    // ── 修改密码 ──────────────────────────────────────────

    /**
     * 修改密码端点 — 首次登录强制修改 / 密码过期修改。
     * <p>
     * 使用 login 时颁发的 changeToken 验证身份，
     * 校验密码强度 + 密码历史，BCrypt 加密后更新。
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(
            @RequestBody ChangePasswordRequest request) {

        String changeToken = request.changeToken();
        String newPassword = request.newPassword();

        if (changeToken == null || changeToken.isEmpty()) {
            return ResponseEntity.status(400).body(ApiResponse.badRequest("changeToken不能为空"));
        }
        if (newPassword == null || newPassword.isEmpty()) {
            return ResponseEntity.status(400).body(ApiResponse.badRequest("newPassword不能为空"));
        }

        // ── T2-1: 验证 changeToken ──
        String userId;
        try {
            Claims claims = jwtTokenProvider.validateToken(changeToken);
            String type = claims.get("type", String.class);
            String purpose = claims.get("purpose", String.class);
            if (!"change-password".equals(type) && !"change-password".equals(purpose)) {
                return ResponseEntity.status(401)
                        .body(ApiResponse.unauthorized("Token类型无效，请使用change-password token"));
            }
            userId = claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return ResponseEntity.status(401).body(ApiResponse.unauthorized("changeToken无效或已过期"));
        }

        // 查询用户（需要 password_hash 和 password_history）
        List<Map<String, Object>> users = authService.findUserForPasswordChange(userId);

        if (users.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.notFound("用户不存在"));
        }

        Map<String, Object> user = users.get(0);
        String currentPasswordHash = (String) user.get("password_hash");

        // ── T2-2: 密码强度校验 ──
        String strengthError = validatePasswordStrength(newPassword);
        if (strengthError != null) {
            return ResponseEntity.status(400).body(ApiResponse.badRequest(strengthError));
        }

        // ── T2-3: 密码历史校验（不重复最近 N 条） ──
        int historyCount = getIntConfig("password_history_count", 3);
        String passwordHistoryJson = (String) user.get("password_history");
        if (passwordHistoryJson != null && !passwordHistoryJson.isEmpty()) {
            try {
                List<String> historyHashes = objectMapper.readValue(
                        passwordHistoryJson, new TypeReference<List<String>>() {});
                // 取最近 N 条进行检查
                int checkCount = Math.min(historyCount, historyHashes.size());
                for (int i = historyHashes.size() - checkCount; i < historyHashes.size(); i++) {
                    if (passwordEncoder.matches(newPassword, historyHashes.get(i))) {
                        return ResponseEntity.status(400)
                                .body(ApiResponse.badRequest("新密码不能与最近使用的" + historyCount + "个密码相同"));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse password_history for user {}: {}", userId, e.getMessage());
            }
        }

        // ── T2-4: BCrypt 加密新密码 ──
        String newPasswordHash = passwordEncoder.encode(newPassword);

        // ── T2-5/6: 更新密码、取消强制改密标记、更新密码历史 ──
        List<String> historyList = new ArrayList<>();
        if (passwordHistoryJson != null && !passwordHistoryJson.isEmpty() && !"[]".equals(passwordHistoryJson)) {
            try {
                historyList = objectMapper.readValue(passwordHistoryJson,
                        new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse password_history for user {}: {}", userId, e.getMessage());
            }
        }
        historyList.add(newPasswordHash);
        // 只保留最近 historyCount 条
        while (historyList.size() > historyCount) {
            historyList.remove(0);
        }
        String updatedHistoryJson;
        try {
            updatedHistoryJson = objectMapper.writeValueAsString(historyList);
        } catch (Exception e) {
            updatedHistoryJson = "[]";
        }

        authService.updatePassword(newPasswordHash, updatedHistoryJson, userId);

        log.info("Password changed successfully for userId: {}", userId);

        Map<String, String> data = Map.of("message", "密码修改成功，请重新登录");
        return ResponseEntity.ok(ApiResponse.success("密码修改成功", data));
    }

    // ── 密码强度校验 ──────────────────────────────────────

    /**
     * 校验密码强度。返回 null 表示通过，否则返回错误提示。
     */
    private String validatePasswordStrength(String password) {
        int minLength = getIntConfig("password_min_length", 8);
        boolean requireUpper = getBoolConfig("password_require_upper", true);
        boolean requireDigit = getBoolConfig("password_require_digit", true);
        boolean requireSpecial = getBoolConfig("password_require_special", false);

        if (password.length() < minLength) {
            return "密码长度至少" + minLength + "位";
        }
        if (requireUpper && !password.matches(".*[A-Z].*")) {
            return "密码必须包含至少一个大写字母";
        }
        if (requireDigit && !password.matches(".*[0-9].*")) {
            return "密码必须包含至少一个数字";
        }
        if (requireSpecial && !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            return "密码必须包含至少一个特殊字符";
        }
        return null;
    }

    // ── Token 刷新 ────────────────────────────────────────

    /**
     * 使用 Refresh Token 换取新的 Access Token。
     * <p>
     * 请求体：{ "refreshToken": "..." }
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(@RequestBody RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.status(400).body(ApiResponse.badRequest("refreshToken不能为空"));
        }

        try {
            Claims claims = jwtTokenProvider.validateToken(refreshToken);

            // 验证是 refresh token
            if (!"refresh".equals(claims.get("type"))) {
                return ResponseEntity.status(401).body(ApiResponse.unauthorized("Token类型无效，请使用refreshToken"));
            }

            // 验证是否在存储中（防止已吊销的 refresh token）
            String storedUserId = refreshTokens.get(refreshToken);
            if (storedUserId == null) {
                return ResponseEntity.status(401).body(ApiResponse.unauthorized("Refresh Token已吊销"));
            }

            String userId = claims.getSubject();
            if (!userId.equals(storedUserId)) {
                return ResponseEntity.status(401).body(ApiResponse.unauthorized("Refresh Token不匹配"));
            }

            // 签发新的 Access Token
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            if (roles == null) roles = List.of();
            String newAccessToken = jwtTokenProvider.createAccessToken(userId, roles);

            // 可选：轮换 Refresh Token（签发新的，使旧的失效）
            String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
            refreshTokens.remove(refreshToken);
            refreshTokens.put(newRefreshToken, userId);

            log.info("Token refreshed for userId: {}", userId);

            TokenRefreshResponse data = new TokenRefreshResponse(newAccessToken, newRefreshToken);
            return ResponseEntity.ok(ApiResponse.success("Token刷新成功", data));

        } catch (JwtException | IllegalArgumentException e) {
            return ResponseEntity.status(401).body(ApiResponse.unauthorized("Refresh Token无效或已过期"));
        }
    }

    // ── 工具方法 ──────────────────────────────────────────

    /**
     * 解析 roles 字段（JSON 数组字符串 → List&lt;String&gt;）。
     */
    private List<String> parseRoles(String rolesJson) {
        if (rolesJson == null || rolesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(rolesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse roles JSON: {}", rolesJson, e);
            return Collections.emptyList();
        }
    }

    // ── R1.1 工作站校验辅助方法 ──────────────────────────

    /**
     * R1.1: 从 HttpServletRequest 提取客户端真实 IP。
     * 优先 X-Forwarded-For / X-Real-IP，回退到 remoteAddr。
     */
    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能含逗号分隔的多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * R1.1: 判断客户端 IP 是否匹配绑定的工作站。
     * <p>linked_workstation 可配置为单个 IP，或逗号/分号分隔的多个 IP。
     * 匹配规则：大小写不敏感，去除空白后精确匹配任一配置项。
     * 配置为 "*" 表示不限制（放行）。
     */
    private boolean workstationMatches(String linkedWorkstation, String clientIp) {
        if (linkedWorkstation == null || linkedWorkstation.isBlank()) {
            return true; // 无绑定 → 放行
        }
        if (clientIp == null || clientIp.isBlank()) {
            return false; // 有绑定但无法获取客户端IP → 拒绝
        }
        String[] allowed = linkedWorkstation.split("[,;]");
        String ip = clientIp.trim();
        for (String entry : allowed) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            if ("*".equals(e) || e.equalsIgnoreCase(ip)) {
                return true;
            }
        }
        return false;
    }
}
