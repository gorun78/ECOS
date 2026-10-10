package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextPack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * contextDigest 计算器（§5.4-9 / §5.6 稳定性测试）—— 对
 * {@link ContextPack#normalizedDigestView()}（固定字段序 {@link java.util.LinkedHashMap}）
 * 做确定性 JSON 序列化后 SHA-256。
 *
 * <p>可复现保证：相同来源版本、相同请求、相同策略 ⇒ 相同 digest。
 * 序列化器统一 {@code SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS}
 * （视图内 Map 键序强制字典序），列表序保持装配序（权威源清单输入序）。</p>
 */
public final class ContextDigestCalculator {

    /** digest 前缀字面量（§5.2 响应示例 "sha256:..."）。 */
    public static final String HASH_PREFIX = "sha256:";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private ContextDigestCalculator() {
        throw new UnsupportedOperationException("工具类，禁止实例化");
    }

    /** 计算规范化 Pack 的 SHA-256 摘要（返回 {@code sha256:<64hex>}）。 */
    public static String digest(ContextPack pack) {
        try {
            byte[] json = MAPPER.writeValueAsString(pack.normalizedDigestView())
                    .getBytes(StandardCharsets.UTF_8);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(json);
            StringBuilder sb = new StringBuilder(HASH_PREFIX);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("contextDigest 计算失败（SHA-256/Jackson 不可用属环境故障）", e);
        }
    }
}
