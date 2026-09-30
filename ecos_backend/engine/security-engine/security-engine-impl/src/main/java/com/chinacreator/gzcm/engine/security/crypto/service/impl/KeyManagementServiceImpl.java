package com.chinacreator.gzcm.engine.security.crypto.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 详细设计-01 C.2.6 / W37（P0）改造：
 * <ol>
 *   <li>主密钥<b>仅从</b> {@code ECOS_MASTER_KEY}（env）读取；缺失即返回 {@code null}，
 *       由上层 {@link com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl}
 *       转成 {@link IKeyManagementService.KeyNotFoundException} 抛给端点映射 500
 *       {@code ECOS-SEC-430}。<b>加解密链路绝不自动建钥。</b></li>
 *   <li>{@link #createKey} 保留为运维侧显式建钥入口（构建期 / eBOM 生成种子），
 *       禁止生产环境运行期调用。</li>
 * </ol>
 *
 * <p>密钥形态约定：env 值 base64 解码后须为 16/24/32 字节；若已 base64 且长度对不上，
 * 退化为 raw UTF-8 字节（收敛至 32B 并补零）以便开发期快速联调，生产必须走 KMS 注入。</p>
 */
public class KeyManagementServiceImpl implements IKeyManagementService {

    private static final Logger log = LoggerFactory.getLogger(KeyManagementServiceImpl.class);
    /** env 变量名（C.2.6）。 */
    public static final String ENV_MASTER_KEY = "ECOS_MASTER_KEY";
    /** 默认密钥 id：env 主密钥对应的逻辑名。 */
    public static final String DEFAULT_KEY_ID = "master";

    private final Map<String, Key> keyCache = new HashMap<>();
    private final byte[] masterBytes;

    public KeyManagementServiceImpl() {
        this.masterBytes = readMasterKeyFromEnv();
        if (masterBytes == null) {
            log.warn("主密钥缺失: {} 未设置（写操作将拒绝并映射 ECOS-SEC-430，加解密不静默建钥）",
                    ENV_MASTER_KEY);
        } else {
            log.info("主密钥已从 env 加载（length={}B，不入日志/密钥不入文件）", masterBytes.length);
        }
    }

    /** env → AES 密钥字节；缺失或非法 → null（触发上层 KeyNotFoundException）。 */
    private static byte[] readMasterKeyFromEnv() {
        String raw = System.getenv(ENV_MASTER_KEY);
        if (raw == null || raw.isBlank()) return null;
        try {
            byte[] b = Base64.getDecoder().decode(raw.trim());
            if (b.length == 16 || b.length == 24 || b.length == 32) return b;
        } catch (IllegalArgumentException ignored) { /* 非 base64，走 raw */ }
        byte[] rawUtf8 = raw.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[32];
        int n = Math.min(rawUtf8.length, 32);
        System.arraycopy(rawUtf8, 0, out, 0, n);
        return out;
    }

    @Override
    public Key getKey(String keyId) throws KeyManagementException {
        // 逻辑 id 等价于 env 主密钥
        if (keyId == null || keyId.isBlank() || DEFAULT_KEY_ID.equals(keyId)
                || keyId.equals("<" + DEFAULT_KEY_ID + ">")) {
            if (masterBytes == null) return null;
            return new SecretKeySpec(masterBytes, "AES");
        }
        return keyCache.get(keyId);
    }

    @Override
    public byte[] getKeyBytes(String keyId) throws KeyManagementException {
        Key k = getKey(keyId);
        return k == null ? null : k.getEncoded();
    }

    @Override
    public Key createKey(String keyId, String algorithm, int keySize) throws KeyManagementException {
        // 仅运维显式路径；生产运行期禁止调用（W37 门禁：DataEncryptionServiceImpl 不再自动调本方法）
        log.warn("调用 createKey 属运维权限动作（keyId={}）：生产运行期加解密链路禁止（W37）", keyId);
        try {
            javax.crypto.KeyGenerator generator = javax.crypto.KeyGenerator.getInstance(
                    algorithm == null ? "AES" : algorithm);
            if (keySize > 0) generator.init(keySize);
            SecretKey key = generator.generateKey();
            keyCache.put(keyId, key);
            return key;
        } catch (Exception e) {
            throw new KeyManagementException("Failed to create key", e);
        }
    }
}
