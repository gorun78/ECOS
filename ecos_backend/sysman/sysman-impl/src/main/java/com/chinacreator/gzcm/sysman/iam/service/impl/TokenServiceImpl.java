package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.chinacreator.gzcm.sysman.iam.service.ITokenService;
import com.chinacreator.gzcm.sysman.iam.service.ISessionService;

/**
 * Token服务实现类
 * 
 * @author CDRC Security Team
 */
@Service
public class TokenServiceImpl implements ITokenService {
    
    private static final String SECRET_KEY = "cdrc-databridge-secret-key-for-jwt-token-generation-2024";
    /** Access Token 默认过期时间 (毫秒): 30 分钟 */
    private static final long DEFAULT_ACCESS_TOKEN_EXPIRATION = 30 * 60 * 1000;
    /** Refresh Token 默认过期时间 (毫秒): 7 天 */
    private static final long DEFAULT_REFRESH_TOKEN_EXPIRATION = 7 * 24 * 3600 * 1000;

    private SecretKey signingKey;
    private Map<String, Long> blacklist = new ConcurrentHashMap<>();
    private ISessionService sessionService;
    private final SysConfigService sysConfigService;
    
    /**
     * 构造函数
     * 
     * @param sessionService 会话服务
     */
    public TokenServiceImpl(ISessionService sessionService, SysConfigService sysConfigService) {
        this.sessionService = sessionService;
        this.sysConfigService = sysConfigService;
        // 生成签名密钥
        byte[] keyBytes = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /** 从 SysConfigService 获取 Access Token 过期时间（毫秒），默认 30 分钟 */
    private long getAccessTokenExpiration() {
        int minutes = sysConfigService.getInt("session.timeout_minutes", 30);
        return (long) minutes * 60 * 1000;
    }
    
    @Override
    public String generateAccessToken(String userId, String username, Map<String, Object> claims) throws TokenException {
        try {
            Date now = new Date();
            Date expiration = new Date(now.getTime() + getAccessTokenExpiration());
            
            var cb = Jwts.claims()
                .subject(userId)
                .issuedAt(now)
                .expiration(expiration)
                .add("username", username);
            
            // jjwt 0.12 .build()返回不可变Claims — 必须在ClaimsBuilder阶段注入
            if (claims != null && !claims.isEmpty()) {
                cb.add(claims);
            }
            
            return Jwts.builder()
                .claims(cb.build())
                .signWith(signingKey)
                .compact();
                
        } catch (Exception e) {
            throw new TokenException("生成Access Token失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String generateRefreshToken(String userId, String username) throws TokenException {
        try {
            Date now = new Date();
            Date expiration = new Date(now.getTime() + DEFAULT_REFRESH_TOKEN_EXPIRATION);
            
            Claims claims = Jwts.claims()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .build();
            
            claims.put("username", username);
            claims.put("type", "refresh");
            
            return Jwts.builder()
                .setClaims(claims)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
                
        } catch (Exception e) {
            throw new TokenException("生成Refresh Token失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, Object> validateToken(String token) throws TokenException {
        try {
            // 检查黑名单
            if (isBlacklisted(token)) {
                throw new TokenException("Token已被撤销");
            }
            
            Claims claims = Jwts.parser()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
            
            Map<String, Object> result = new HashMap<>();
            result.put("userId", claims.getSubject());
            result.put("username", claims.get("username"));
            result.put("issuedAt", claims.getIssuedAt());
            result.put("expiration", claims.getExpiration());
            result.putAll(claims);
            
            return result;
            
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new TokenException("Token已过期");
        } catch (io.jsonwebtoken.JwtException e) {
            throw new TokenException("Token验证失败: " + e.getMessage());
        } catch (Exception e) {
            throw new TokenException("Token验证失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void addToBlacklist(String token, long expirationTime) throws TokenException {
        try {
            long expiration = System.currentTimeMillis() + expirationTime * 1000;
            blacklist.put(token, expiration);
        } catch (Exception e) {
            throw new TokenException("添加到黑名单失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isBlacklisted(String token) throws TokenException {
        try {
            Long expiration = blacklist.get(token);
            if (expiration == null) {
                return false;
            }
            
            // 如果已过期，从黑名单中移除
            if (System.currentTimeMillis() > expiration) {
                blacklist.remove(token);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            throw new TokenException("检查黑名单失败: " + e.getMessage(), e);
        }
    }
}

