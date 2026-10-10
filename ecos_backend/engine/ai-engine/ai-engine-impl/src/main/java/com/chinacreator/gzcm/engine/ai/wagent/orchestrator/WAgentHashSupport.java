package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 确定性哈希单源（详细设计-10 §4.4 幂等算法 + F10-14 input_hash/output_hash）。
 *
 * <p>数字不来自 LLM：sha256 为纯确定性计算，输入原文只进摘要、不落库
 * （{@code *_ref_text} 引用 + {@code *_hash} 实列，§6.3 引用原则）。</p>
 */
public final class WAgentHashSupport {

    private WAgentHashSupport() {}

    /** sha256 十六进制（64 字符，V233/V234 hash 列 VARCHAR(64) 恰容）。 */
    public static String sha256Hex(String text) {
        if (text == null) {
            throw new IllegalArgumentException("sha256 输入不得为 null（禁把 null 当空串静默哈希）");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺 SHA-256 算法（不符合 Java 17 基线）", e);
        }
    }

    /**
     * 幂等键散列（§4.4 冻结形态）：{@code sha256(tenant + "|" + initiated_by + "|" + raw_key)}。
     * 分隔符固定，防拼接歧义（"ab"+"c" 与 "a"+"bc" 不同键）。
     */
    public static String idempotencyHash(String tenantId, String initiatedBy, String rawKey) {
        return sha256Hex(tenantId + "|" + initiatedBy + "|" + rawKey);
    }
}
