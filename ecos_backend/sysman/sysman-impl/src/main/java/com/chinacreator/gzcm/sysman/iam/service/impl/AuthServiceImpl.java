package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;
import com.chinacreator.gzcm.sysman.iam.service.IAuthService;
import com.chinacreator.gzcm.sysman.iam.service.ITokenService;
import com.chinacreator.gzcm.sysman.iam.service.ISessionService;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;

/**
 * 认证服务实现类
 * 
 * @author CDRC Security Team
 */
@Service
public class AuthServiceImpl implements IAuthService {
    
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    
    private ITokenService tokenService;
    private ISessionService sessionService;
    private IUserService userService;
    private final SysConfigService sysConfigService;
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * 构造函数
     * 
     * @param tokenService Token服务
     * @param sessionService 会话服务
     */
    @Autowired
    public AuthServiceImpl(ITokenService tokenService, ISessionService sessionService, IUserService userService,
                           SysConfigService sysConfigService, JdbcTemplate jdbcTemplate) {
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.userService = userService;
        this.sysConfigService = sysConfigService;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public Map<String, Object> login(String username, String password, String ipAddress) throws AuthException {
        try {
            if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
                throw new AuthException("INVALID_CREDENTIALS", "用户名或密码不能为空");
            }
            // 用户验证
            UserAccount user = userService.getUserByUsername(username);
            if (user == null) {
                throw new AuthException("INVALID_CREDENTIALS", "用户不存在");
            }
            if ("INACTIVE".equalsIgnoreCase(user.getStatus()) || "DELETED".equalsIgnoreCase(user.getStatus())) {
                throw new AuthException("USER_DISABLED", "用户已禁用或删除");
            }
            boolean ok = userService.verifyPassword(user.getUserId(), password);
            if (!ok) {
                throw new AuthException("INVALID_CREDENTIALS", "用户名或密码错误");
            }
            if ("1".equals(user.getLocked())) {
                throw new AuthException("USER_LOCKED", "用户已锁定");
            }

            // 密码过期检查（使用 updatedTime 作为最后密码修改时间）
            int expireDays = sysConfigService.getInt("password.expire_days", 0);
            if (expireDays > 0 && user.getUpdatedTime() != null) {
                java.time.LocalDateTime expireTime = user.getUpdatedTime().plusDays(expireDays);
                if (java.time.LocalDateTime.now().isAfter(expireTime)) {
                    throw new AuthException("PASSWORD_EXPIRED",
                        "密码已过期（有效期" + expireDays + "天），请联系管理员重置密码");
                }
            }
            
            String userId = user.getUserId();
            
            // 生成Token
            Map<String, Object> claims = new HashMap<>();
            claims.put("ipAddress", ipAddress);
            claims.put("username", username);
            
            // 从数据库获取租户ID并写入JWT
            String tenantId = getUserTenantId(username);
            if (tenantId != null) {
                claims.put("tenant_id", tenantId);
            }
            
            String accessToken = tokenService.generateAccessToken(userId, username, claims);
            String refreshToken = tokenService.generateRefreshToken(userId, username);
            
            // 创建会话
            String sessionId = sessionService.createSession(userId, username, ipAddress, "Unknown");
            
            // 计算过期时间（秒），与 TokenServiceImpl 一致
            int timeoutMinutes = sysConfigService.getInt("session.timeout_minutes", 30);
            int expiresIn = timeoutMinutes * 60;
            
            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("accessToken", accessToken);
            result.put("refreshToken", refreshToken);
            result.put("sessionId", sessionId);
            result.put("userId", userId);
            result.put("username", username);
            result.put("tokenType", "Bearer");
            result.put("expiresIn", expiresIn);
            
            return result;
            
        } catch (ITokenService.TokenException e) {
            throw new AuthException("TOKEN_GENERATION_FAILED", "Token生成失败: " + e.getMessage(), e);
        } catch (ISessionService.SessionException e) {
            throw new AuthException("SESSION_CREATION_FAILED", "会话创建失败: " + e.getMessage(), e);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("LOGIN_FAILED", "登录失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void logout(String token) throws AuthException {
        try {
            Map<String, Object> claims = tokenService.validateToken(token);
            long expirationTime = ((java.util.Date) claims.get("expiration")).getTime() / 1000 
                - System.currentTimeMillis() / 1000;
            if (expirationTime > 0) {
                tokenService.addToBlacklist(token, expirationTime);
            }
            if (claims.containsKey("sessionId")) {
                String sessionId = (String) claims.get("sessionId");
                sessionService.deleteSession(sessionId);
            }
            
        } catch (ITokenService.TokenException e) {
            throw new AuthException("TOKEN_VALIDATION_FAILED", "Token验证失败: " + e.getMessage(), e);
        } catch (ISessionService.SessionException e) {
            // 会话删除失败不影响登出
        } catch (Exception e) {
            throw new AuthException("LOGOUT_FAILED", "登出失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, Object> refreshToken(String refreshToken) throws AuthException {
        try {
            // 验证Refresh Token
            Map<String, Object> claims = tokenService.validateToken(refreshToken);
            
            // 验证Token类型必须是refresh
            String tokenType = (String) claims.get("type");
            if (!"refresh".equals(tokenType)) {
                throw new AuthException("INVALID_TOKEN_TYPE", "Token类型错误，必须是Refresh Token");
            }
            
            String userId = (String) claims.get("userId");
            String username = (String) claims.get("username");
            
            // 验证用户状态
            UserAccount user = userService.getUserByUsername(username);
            if (user == null || "INACTIVE".equalsIgnoreCase(user.getStatus()) || "DELETED".equalsIgnoreCase(user.getStatus())) {
                throw new AuthException("USER_DISABLED", "用户已禁用或删除");
            }
            
            // 生成新的Access Token
            Map<String, Object> newClaims = new HashMap<>();
            String accessToken = tokenService.generateAccessToken(userId, username, newClaims);
            
            // 生成新的Refresh Token（实现Token轮换，提高安全性）
            String newRefreshToken = tokenService.generateRefreshToken(userId, username);
            
            // 将旧的Refresh Token加入黑名单
            java.util.Date expiration = (java.util.Date) claims.get("expiration");
            long expirationTime = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            if (expirationTime > 0) {
                tokenService.addToBlacklist(refreshToken, expirationTime);
            }
            
            // 计算过期时间（秒）
            int timeoutMinutes = sysConfigService.getInt("session.timeout_minutes", 30);
            int expiresIn = timeoutMinutes * 60;
            
            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("accessToken", accessToken);
            result.put("refreshToken", newRefreshToken); // 返回新的Refresh Token
            result.put("tokenType", "Bearer");
            result.put("expiresIn", expiresIn);
            
            return result;
            
        } catch (ITokenService.TokenException e) {
            throw new AuthException("TOKEN_REFRESH_FAILED", "Token刷新失败: " + e.getMessage(), e);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("REFRESH_FAILED", "刷新失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, Object> validateToken(String token) throws AuthException {
        try {
            return tokenService.validateToken(token);
        } catch (ITokenService.TokenException e) {
            throw new AuthException("TOKEN_VALIDATION_FAILED", "Token验证失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new AuthException("VALIDATION_FAILED", "验证失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取用户所属租户ID。
     * 优先从 TD_USER.TENANT_ID 列查询，若表中无此列或未配置则默认返回 "tenant-a"。
     */
    private String getUserTenantId(String username) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT \"TENANT_ID\" FROM TD_USER WHERE \"USERNAME\" = ?", username);
            if (rows != null && !rows.isEmpty()) {
                Object tid = rows.get(0).get("TENANT_ID");
                if (tid != null && !tid.toString().isBlank()) {
                    return tid.toString();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to query TENANT_ID from TD_USER for {}: {}. Falling back to default.", 
                username, e.getMessage());
        }
        // 默认租户
        return "tenant-a";
    }
}
