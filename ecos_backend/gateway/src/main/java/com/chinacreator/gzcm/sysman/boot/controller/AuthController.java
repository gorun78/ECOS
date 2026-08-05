package com.chinacreator.gzcm.sysman.boot.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.sql.Timestamp;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证控制器 — 登录 / 获取用户信息 / 刷新 Token。
 * <p>
 * 数据库用户表验证 + BCrypt 密码比对。
 */
@RestController
@RequestMapping({"/api/v1/auth", "/auth"})
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 内存中的 Refresh Token 存储（生产环境应使用 Redis） */
    private static final Map<String, String> refreshTokens = new ConcurrentHashMap<>();

    public AuthController(JwtTokenProvider jwtTokenProvider, JdbcTemplate jdbcTemplate) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@RequestBody LoginRequest request) {
        String username = request.username();
        String password = request.password();

        // 查询用户（含安全字段）
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, username, password_hash, display_name, roles, "
                + "failed_attempts, locked_until, password_change_required, "
                + "last_password_change, password_history "
                + "FROM users WHERE username = ? AND enabled = true",
                username);

        if (users.isEmpty()) {
            log.warn("Login failed — user not found: {}", username);
            return ResponseEntity.status(401).body(ApiResponse.unauthorized("用户名或密码错误"));
        }

        Map<String, Object> user = users.get(0);
        String passwordHash = (String) user.get("password_hash");

        // ── T1-1: 检查账户是否被锁定 ──
        Timestamp lockedUntil = (Timestamp) user.get("locked_until");
        if (lockedUntil != null && lockedUntil.toInstant().isAfter(java.time.Instant.now())) {
            log.warn("Login blocked — account locked until {}: {}", lockedUntil, username);
            return ResponseEntity.status(423)
                    .body(ApiResponse.error(423, "ACCOUNT_LOCKED", "账户已锁定，请稍后再试"));
        }

        // BCrypt 密码验证
        if (!passwordEncoder.matches(password, passwordHash)) {
            // ── T1-2: 密码错误 — 增加失败计数 ──
            int failedAttempts = user.get("failed_attempts") != null
                    ? ((Number) user.get("failed_attempts")).intValue() : 0;
            failedAttempts++;

            int maxLoginAttempts = getIntConfig("max_login_attempts", 5);
            int lockoutDurationMinutes = getIntConfig("lockout_duration_minutes", 15);

            if (failedAttempts >= maxLoginAttempts) {
                jdbcTemplate.update(
                    "UPDATE users SET failed_attempts = ?, locked_until = NOW() + (? * INTERVAL '1 minute') WHERE username = ?",
                    failedAttempts, lockoutDurationMinutes, username);
                log.warn("Account locked — {} failed attempts for user: {}", failedAttempts, username);
                return ResponseEntity.status(423)
                        .body(ApiResponse.error(423, "ACCOUNT_LOCKED",
                                "账户已锁定" + lockoutDurationMinutes + "分钟，请稍后再试"));
            } else {
                int remaining = maxLoginAttempts - failedAttempts;
                jdbcTemplate.update("UPDATE users SET failed_attempts = ? WHERE username = ?",
                        failedAttempts, username);
                log.warn("Login failed — wrong password for user: {} (attempt {}/{})",
                        username, failedAttempts, maxLoginAttempts);
                return ResponseEntity.status(401).body(ApiResponse.unauthorized(
                        "用户名或密码错误（剩余尝试" + remaining + "次）"));
            }
        }

        // ── 密码正确 — 检查是否需要改密 ──
        Boolean pwdChangeRequired = (Boolean) user.get("password_change_required");
        if (Boolean.TRUE.equals(pwdChangeRequired)) {
            String changeToken = jwtTokenProvider.createAccessToken((String) user.get("id"),
                    parseRoles((String) user.get("roles")));
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("changeToken", changeToken);
            data.put("message", "首次登录，请修改密码");
            return ResponseEntity.ok(ApiResponse.success("PASSWORD_CHANGE_REQUIRED", data));
        }

        // ── 检查密码是否过期 ──
        Timestamp lastPwdChange = (Timestamp) user.get("last_password_change");
        int expireDays = getIntConfig("password_expire_days", 90);
        if (lastPwdChange != null) {
            long daysSince = java.time.Duration.between(
                    lastPwdChange.toInstant(), java.time.Instant.now()).toDays();
            if (daysSince > expireDays) {
                String changeToken = jwtTokenProvider.createAccessToken((String) user.get("id"),
                        parseRoles((String) user.get("roles")));
                Map<String, Object> data = new java.util.LinkedHashMap<>();
                data.put("changeToken", changeToken);
                data.put("message", "密码已过期" + daysSince + "天，请修改");
                return ResponseEntity.ok(ApiResponse.success("PASSWORD_EXPIRED", data));
            }
        }

        // ── 重置失败计数 ──
        jdbcTemplate.update("UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = ?",
                user.get("id"));

        // 提取用户信息
        String userId = (String) user.get("id");
        List<String> roles = parseRoles((String) user.get("roles"));

        // 签发 JWT
        String accessToken = jwtTokenProvider.createAccessToken(userId, roles);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        // 存储 Refresh Token（以便吊销）
        refreshTokens.put(refreshToken, userId);

        log.info("User {} (id={}) logged in, access token issued", username, userId);

        LoginResponse data = new LoginResponse(accessToken, refreshToken, username, userId, roles);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

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
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT username, display_name, roles FROM users WHERE id = ? AND enabled = true",
                    userId);

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

    // ── T2: change-password ───────────────────────────────────

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(@RequestBody Map<String, Object> body) {
        String changeToken = (String) body.get("changeToken");
        String newPassword = (String) body.get("newPassword");
        if (changeToken == null || newPassword == null) {
            return ResponseEntity.badRequest().body(ApiResponse.badRequest("changeToken和newPassword不能为空"));
        }

        // 验证 changeToken JWT
        String userId;
        try {
            Claims claims = jwtTokenProvider.validateToken(changeToken);
            userId = claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "INVALID_TOKEN", "changeToken无效或已过期"));
        }

        // 校验密码强度
        String strengthError = validatePasswordStrength(newPassword);
        if (strengthError != null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "PASSWORD_WEAK", strengthError));
        }

        // 检查密码历史
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT password_hash, password_history FROM users WHERE id = ?", userId);
        if (users.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.notFound("用户不存在"));
        }
        Map<String, Object> u = users.get(0);
        String oldHash = (String) u.get("password_hash");
        String historyJson = (String) u.get("password_history");
        int historyCount = getIntConfig("password_history_count", 3);

        String newHash = passwordEncoder.encode(newPassword);
        if (passwordEncoder.matches(newPassword, oldHash)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "PASSWORD_REUSED", "新密码不能与当前密码相同"));
        }

        // 更新密码 + 重置标志
        jdbcTemplate.update(
            "UPDATE users SET password_hash = ?, password_change_required = false, "
            + "last_password_change = NOW(), failed_attempts = 0, locked_until = NULL WHERE id = ?",
            newHash, userId);

        Map<String, String> result = new java.util.LinkedHashMap<>();
        result.put("status", "ok");
        result.put("message", "密码修改成功");
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── T5: force-logout (Token强制踢出) ──────────────────────

    /**
     * 强制踢出用户 — 将当前 Token 的 jti 写入黑名单。
     * 请求头需携带 Bearer token，后续该 token 将被拒绝。
     */
    @PostMapping("/force-logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> forceLogout(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(ApiResponse.unauthorized("未登录"));
        }

        try {
            String token = auth.substring(7);
            Claims claims = jwtTokenProvider.validateToken(token);

            String jti = claims.getId();
            String userId = claims.getSubject();
            java.util.Date expiration = claims.getExpiration();

            if (jti == null) {
                return ResponseEntity.status(400).body(ApiResponse.badRequest("Token中缺少jti"));
            }

            // 将 jti 写入黑名单
            String id = java.util.UUID.randomUUID().toString().replace("-", "");
            jdbcTemplate.update(
                "INSERT INTO ecos_token_blacklist (id, jti, user_id, expires_at) VALUES (?,?,?,?)",
                id, jti, userId, new java.sql.Timestamp(expiration.getTime())
            );

            log.info("用户被强制踢出: userId={}, jti={}", userId, jti);

            Map<String, String> result = new java.util.LinkedHashMap<>();
            result.put("status", "ok");
            result.put("message", "用户已被强制踢出");
            result.put("userId", userId);
            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            return ResponseEntity.status(401).body(ApiResponse.unauthorized("Token无效或已过期"));
        }
    }

    // ── 配置读取辅助方法 ──────────────────────────────────────

    private int getIntConfig(String key, int defaultValue) {
        try {
            String val = jdbcTemplate.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = ? AND status = 'active'",
                String.class, key);
            return val != null ? Integer.parseInt(val) : defaultValue;
        } catch (Exception e) { return defaultValue; }
    }

    private boolean getBoolConfig(String key, boolean defaultValue) {
        try {
            String val = jdbcTemplate.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = ? AND status = 'active'",
                String.class, key);
            return val != null ? Boolean.parseBoolean(val) : defaultValue;
        } catch (Exception e) { return defaultValue; }
    }

    private String validatePasswordStrength(String password) {
        int minLen = getIntConfig("password_min_length", 8);
        boolean needUpper = getBoolConfig("password_require_upper", true);
        boolean needDigit = getBoolConfig("password_require_digit", true);
        boolean needSpecial = getBoolConfig("password_require_special", false);

        java.util.List<String> errors = new java.util.ArrayList<>();
        if (password.length() < minLen) errors.add("至少" + minLen + "位");
        if (needUpper && !password.matches(".*[A-Z].*")) errors.add("包含大写字母");
        if (needDigit && !password.matches(".*[0-9].*")) errors.add("包含数字");
        if (needSpecial && !password.matches(".*[!@#$%^&*].*")) errors.add("包含特殊字符");
        return errors.isEmpty() ? null : "需" + String.join("、", errors);
    }
}
