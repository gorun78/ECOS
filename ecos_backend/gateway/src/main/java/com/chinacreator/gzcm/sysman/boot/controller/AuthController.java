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
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        String username = request.username();
        String password = request.password();

        // 查询用户
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, username, password_hash, display_name, roles FROM users WHERE username = ? AND enabled = true",
                username);

        if (users.isEmpty()) {
            log.warn("Login failed — user not found: {}", username);
            return ResponseEntity.status(401).body(ApiResponse.unauthorized("用户名或密码错误"));
        }

        Map<String, Object> user = users.get(0);
        String passwordHash = (String) user.get("password_hash");

        // BCrypt 密码验证
        if (!passwordEncoder.matches(password, passwordHash)) {
            log.warn("Login failed — wrong password for user: {}", username);
            return ResponseEntity.status(401).body(ApiResponse.unauthorized("用户名或密码错误"));
        }

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
}
