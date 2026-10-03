package com.chinacreator.gzcm.common.security.credential;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * W02（详细设计-00 C.2.1/C.5.4）— 服务间凭证 HMAC 签名器。
 *
 * <p>契约：{@code X-ECOS-SERVICE: <serviceId>.<hmac>}；
 * 签名输入 = {@code serviceId + "." + 当前时间戳分钟}（分钟级时间窗，防重放窗口可控，
 * 签名方与验证方同一 JVM 钟即可，跨 JVM 5 分钟漂移容差）。</p>
 *
 * <p>密钥来源（生产必须显式配置）：
 * <ol>
 *   <li>构造参数 sharedSecret（推荐，各制品注入）</li>
 *   <li>环境变量 {@code ECOS_SERVICE_SHARED_SECRET}</li>
 *   <li>开发兜底 {@code dev-only-shared-secret}（仅限本地，生产启动告警）</li>
 * </ol>
 */
public final class EcosServiceCredentialSigner {

    /** 分钟粒度时间窗（秒） */
    private static final long WINDOW_MIN = 60L;

    private final String serviceId;
    private final String sharedSecret;

    public EcosServiceCredentialSigner(String serviceId, String sharedSecret) {
        this.serviceId = serviceId == null || serviceId.isBlank() ? "unknown" : serviceId;
        this.sharedSecret = (sharedSecret == null || sharedSecret.isBlank())
                ? System.getenv().getOrDefault("ECOS_SERVICE_SHARED_SECRET", "dev-only-shared-secret")
                : sharedSecret;
    }

    public EcosServiceCredentialSigner(String serviceId) {
        this(serviceId, null);
    }

    /** 生成 {@code <serviceId>.<minute>.<hmac>} 凭证值（C.2.1 契约 <serviceId>.<sig> 的防重放强化形态） */
    public String sign(long nowSeconds) {
        long minute = nowSeconds / WINDOW_MIN;
        return serviceId + "." + minute + "." + hmac(serviceId + "." + minute);
    }

    public String sign() {
        return sign(System.currentTimeMillis() / 1000L);
    }

    /** 校验凭证值（与 sign 对称）；时间窗 ±5 分钟容差 */
    public static boolean verify(String credential, long nowSeconds, String sharedSecret) {
        if (credential == null || !credential.contains(".")) {
            return false;
        }
        int dot = credential.indexOf('.');
        int lastDot = credential.lastIndexOf('.');
        if (dot == lastDot) {
            return false;
        }
        // 凭证格式：serviceId.<minute>.<hmac>
        String sid = credential.substring(0, dot);
        String sig = credential.substring(lastDot + 1);
        try {
            long minute = Long.parseLong(credential.substring(dot + 1, lastDot));
            long nowMinute = nowSeconds / WINDOW_MIN;
            if (Math.abs(nowMinute - minute) > 5) {
                return false; // 超出重放容差窗
            }
            String expected = hmac(sid + "." + minute, sharedSecret);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    sig.getBytes(StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String hmac(String message) {
        return hmac(message, sharedSecret);
    }

    private static String hmac(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    public String serviceId() {
        return serviceId;
    }
}
