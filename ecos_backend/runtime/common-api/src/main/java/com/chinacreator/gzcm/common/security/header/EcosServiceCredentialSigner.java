package com.chinacreator.gzcm.common.security.header;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 服务间 HMAC-SHA256 凭证工具（C.2.1 X-ECOS-SERVICE = &lt;serviceId&gt;.&lt;hmac&gt;）。
 *
 * <p>六服务共享一个大密钥（生产应由 sysman 签发且按 serviceId 分派；仅此处存静态方法，
 * 真正的密钥位置/轮转策略归 01 册）。goal 内 M0 阶段先做形状与校验路径，
 * 密钥落地路径另由 sysman 定稿后回填。</p>
 *
 * <p>HMAC 抽象层与 {@link HeaderAuthInterceptor.EcosServiceCredentialVerifier#fromAad(String)}
 * 共用同一实现，避免"签验不同源"。</p>
 */
public final class EcosServiceCredentialSigner {

    private EcosServiceCredentialSigner() {}

    /**
     * HMAC-SHA256 → base64url（无填充）。canonicalBody 应为
     * {@code path + "\n" + method + "\n" + traceId} 的固定拼接，all 消费方同一口径。
     */
    public static String hmacSha256Base64Url(String secret, String canonicalBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(canonicalBody.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * 稳定摘要：用于日志/调试，禁作为签名本身（仅用于 diff 两请求是否被签名器"看到同一 body"）。
     */
    public static String sha256Hex(String s) {
        try {
            byte[] raw = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable: " + e.getMessage(), e);
        }
    }

    /** 生成 X-ECOS-SERVICE 头（serviceId + '.' + hmac）。调用方负责共享密钥注入。 */
    public static String generateHeader(String serviceId, String sharedSecret,
                                        String path, String method, String traceId) {
        String body = path + "\n" + (method == null ? "GET" : method).toUpperCase()
                + "\n" + (traceId == null ? "" : traceId);
        return serviceId + "." + hmacSha256Base64Url(sharedSecret, body);
    }
}
