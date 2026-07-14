package com.chinacreator.gzcm.sysman.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JWT Token 签发与验证核心组件。
 * <p>
 * 使用 RS256 签名算法（非对称密钥对），Access Token 15分钟过期，
 * Refresh Token 30天过期。
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** PEM 私钥正则（支持 ---BEGIN PRIVATE KEY--- 格式） */
    private static final Pattern PEM_PRIVATE_KEY_PATTERN =
        Pattern.compile("-----BEGIN\\s+PRIVATE\\s+KEY-----(.*?)-----END\\s+PRIVATE\\s+KEY-----", Pattern.DOTALL);

    /** PEM 公钥正则（支持 ---BEGIN PUBLIC KEY--- 格式） */
    private static final Pattern PEM_PUBLIC_KEY_PATTERN =
        Pattern.compile("-----BEGIN\\s+PUBLIC\\s+KEY-----(.*?)-----END\\s+PUBLIC\\s+KEY-----", Pattern.DOTALL);

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    /** Access Token 有效期：15 分钟 */
    private final long accessTokenExpiration = 15 * 60 * 1000L;

    /** Refresh Token 有效期：30 天 */
    private final long refreshTokenExpiration = 30 * 24 * 60 * 60 * 1000L;

    public JwtTokenProvider(
            @Value("${jwt.private-key}") String privateKeyPemOrBase64,
            @Value("${jwt.public-key}") String publicKeyPemOrBase64) throws Exception {
        this.privateKey = loadPrivateKey(privateKeyPemOrBase64);
        this.publicKey = loadPublicKey(publicKeyPemOrBase64);
        log.info("JwtTokenProvider initialized with RS256 keys");
    }

    /**
     * 创建 Access Token（15分钟有效）。
     *
     * @param userId 用户ID
     * @param roles  用户角色列表
     * @return JWT token string
     */
    public String createAccessToken(String userId, List<String> roles) {
        return createAccessToken(userId, roles, null);
    }

    /**
     * 创建 Access Token（15分钟有效），支持额外 claims。
     *
     * @param userId      用户ID
     * @param roles       用户角色列表
     * @param extraClaims 额外 claims（tenant_id 等），可为 null
     * @return JWT token string
     */
    public String createAccessToken(String userId, List<String> roles, Map<String, Object> extraClaims) {
        Date now = new Date();
        var builder = Jwts.builder()
            .subject(userId)
            .claim("roles", roles)
            .claim("type", "access")
            .issuedAt(now)
            .expiration(new Date(now.getTime() + accessTokenExpiration))
            .id(UUID.randomUUID().toString());
        
        if (extraClaims != null && !extraClaims.isEmpty()) {
            extraClaims.forEach(builder::claim);
        }
        
        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }

    /**
     * 创建 Refresh Token（30天有效）。
     *
     * @param userId 用户ID
     * @return JWT token string
     */
    public String createRefreshToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
            .subject(userId)
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(new Date(now.getTime() + refreshTokenExpiration))
            .id(UUID.randomUUID().toString())
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    }

    /**
     * 校验并解析 Token。
     *
     * @param token JWT token string
     * @return Claims（JWT 载荷）
     * @throws io.jsonwebtoken.JwtException 如果 token 无效或过期
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    // ── Key 加载 ───────────────────────────────────────────

    /**
     * 从 Base64（= 原始 DER 或 PEM 格式）加载 RSA 私钥。
     * 支持两种输入：
     * 1. 纯 Base64 编码的 DER（PKCS#8 格式）
     * 2. PEM 格式（含 -----BEGIN PRIVATE KEY----- 头尾）
     */
    private PrivateKey loadPrivateKey(String pemOrBase64) throws Exception {
        byte[] keyBytes = decodePemOrBase64(pemOrBase64, PEM_PRIVATE_KEY_PATTERN);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    /**
     * 从 Base64（= 原始 DER 或 PEM 格式）加载 RSA 公钥。
     * 支持两种输入：
     * 1. 纯 Base64 编码的 DER（X.509 SubjectPublicKeyInfo 格式）
     * 2. PEM 格式（含 -----BEGIN PUBLIC KEY----- 头尾）
     */
    private PublicKey loadPublicKey(String pemOrBase64) throws Exception {
        byte[] keyBytes = decodePemOrBase64(pemOrBase64, PEM_PUBLIC_KEY_PATTERN);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    /**
     * 从 PEM 或纯 Base64 输入中提取 DER 字节。
     * <p>
     * 如果输入匹配 PEM 模式，则提取 PEM body 中的 Base64 内容并解码；
     * 否则直接视作纯 Base64 编码的 DER 字节进行解码。
     */
    private byte[] decodePemOrBase64(String input, Pattern pemPattern) {
        Matcher matcher = pemPattern.matcher(input);
        if (matcher.find()) {
            // PEM 格式：提取头部/尾部之间的 Base64 内容
            String base64Body = matcher.group(1)
                .replaceAll("\\s", "");  // 移除换行和空白
            return Base64.getDecoder().decode(base64Body);
        }
        // 纯 Base64（可能包含换行）
        String clean = input.replaceAll("\\s", "");
        return Base64.getDecoder().decode(clean);
    }
}
