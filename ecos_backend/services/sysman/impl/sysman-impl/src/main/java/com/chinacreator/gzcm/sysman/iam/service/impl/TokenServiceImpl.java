package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.chinacreator.gzcm.sysman.iam.service.ITokenService;
import com.chinacreator.gzcm.sysman.iam.service.ISessionService;

import jakarta.annotation.PostConstruct;

/**
 * Token服务实现类
 * 
 * <p>B1: Token 黑名单从 ConcurrentHashMap 迁移到 Caffeine 本地缓存 + 分版本持久化。
 * standard 版用 DbBlacklistStore（PG），enterprise/ultimate 版用 RedisBlacklistStore（Redis TTL）。</p>
 *
 * @author CDRC Security Team
 */
@Service
public class TokenServiceImpl implements ITokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenServiceImpl.class);

    private static final String SECRET_KEY = "cdrc-databridge-secret-key-for-jwt-token-generation-2024";
    /** Access Token 默认过期时间 (毫秒): 30 分钟 */
    private static final long DEFAULT_ACCESS_TOKEN_EXPIRATION = 30 * 60 * 1000;
    /** Refresh Token 默认过期时间 (毫秒): 7 天 */
    private static final long DEFAULT_REFRESH_TOKEN_EXPIRATION = 7 * 24 * 3600 * 1000;

    private SecretKey signingKey;

    /** Caffeine 本地缓存：token → expireAt（毫秒），按 token 过期时间自动淘汰 */
    private Cache<String, Long> blacklistCache;

    /** 分版本持久化存储（standard=DB, enterprise/ultimate=Redis），可为 null（纯内存降级） */
    @Autowired(required = false)
    private BlacklistStore blacklistStore;

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

    /**
     * 初始化 Caffeine 缓存 + 从持久化存储加载未过期黑名单
     */
    @PostConstruct
    public void initBlacklist() {
        // Caffeine: maximumSize 防内存溢出，expireAfterWrite 用 refresh token 最大 TTL 兜底
        // 实际过期在 isBlacklisted 中兜底检查精确时间戳
        blacklistCache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(DEFAULT_REFRESH_TOKEN_EXPIRATION, TimeUnit.MILLISECONDS)
            .build();

        // 从持久化存储加载未过期项
        if (blacklistStore != null) {
            try {
                Map<String, Long> loaded = blacklistStore.load();
                if (loaded != null && !loaded.isEmpty()) {
                    loaded.forEach((token, expireAt) -> {
                        if (expireAt > System.currentTimeMillis()) {
                            blacklistCache.put(token, expireAt);
                        }
                    });
                    log.info("Token 黑名单从持久化存储加载 {} 条", loaded.size());
                }
            } catch (Exception e) {
                log.warn("Token 黑名单加载失败，启动时黑名单为空: {}", e.getMessage());
            }
        } else {
            log.info("BlacklistStore 未注入，Token 黑名单使用纯内存模式（Caffeine only）");
        }
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
            // 写入 Caffeine 本地缓存
            blacklistCache.put(token, expiration);
            // 同步写入持久化存储
            if (blacklistStore != null) {
                blacklistStore.save(token, expiration);
            }
        } catch (Exception e) {
            throw new TokenException("添加到黑名单失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isBlacklisted(String token) throws TokenException {
        try {
            Long expiration = blacklistCache.getIfPresent(token);
            if (expiration == null) {
                return false;
            }

            // Caffeine 的 Expiry 已自动过期，但兜底检查
            if (System.currentTimeMillis() > expiration) {
                blacklistCache.invalidate(token);
                if (blacklistStore != null) {
                    blacklistStore.remove(token);
                }
                return false;
            }

            return true;
        } catch (Exception e) {
            throw new TokenException("检查黑名单失败: " + e.getMessage(), e);
        }
    }
}
