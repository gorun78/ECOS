package com.chinacreator.gzcm.ai.wagent.orchestrator;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 分册10 F10-11 · Run 幂等键 sha256(tenant_id + initiated_by + idempotency_key)。
 *
 * <p>同一键去重：应用侧先查后插 + DB {@code UNIQUE(tenant_id, initiated_by, idempotency_key)}
 * 兜底；并发同 key 时返回既有 {@code run_id}。失败路径 UNIQUE 违例 ⇒ 409 E-IDEMPOTENT-CONFLICT + 回传既有 run_id。</p>
 */
public final class RunIdempotency {

    private RunIdempotency() {}

    public static String deriveKey(String tenantId, String initiatedBy, String idempotencyKey) {
        if (tenantId == null) throw new NullPointerException("tenantId null");
        if (initiatedBy == null) throw new NullPointerException("initiatedBy null");
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            throw new IllegalArgumentException("idempotencyKey 缺失：F10-11 强制要求");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((tenantId + "|" + initiatedBy + "|" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable (JCE 缺失)", e);
        }
    }

    public static boolean isValidKey(String key) {
        return key != null && key.length() == 64 && key.chars().allMatch(c ->
                (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'));
    }
}
