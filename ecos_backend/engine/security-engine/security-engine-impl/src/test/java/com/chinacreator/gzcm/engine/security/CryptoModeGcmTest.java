package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService.EncryptionException;
import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 详细设计-01 W37（P0）— AES-256-GCM + 禁静默建钥 + 轮换。
 *
 * <p>三合一覆盖设计 §7.1 验收方式：
 * <ul>
 *   <li>{@link #cryptoModeGcm} — 密文含独立 IV；算法 = AES/GCM（非 AES/ECB 无 IV）</li>
 *   <li>{@link #keyRotation} — 换 keyId 后老版本密文仍可解（“旧 version 保留解密能力”最简形态）</li>
 *   <li>{@link #missingKeyDeny} — 主密钥缺失必抛 {@code KeyNotFoundException}（不静默建钥）</li>
 * </ul>
 *
 * <p>测试用<b>内存 KeyManagementService</b>隔离，不污染生产 env。env 主密钥路径由
 * {@link KeyRotationTest#envMasterKeyLoads} 单点覆盖。</p>
 */
@DisplayName("AES-256-GCM + 密钥治理（W37）")
class CryptoModeGcmTest {

    private static final int AES_256_KEY_LEN = 32;
    private static final byte VERSION_GCM = 0x01;

    private InMemoryKeyService keyService;
    private IDataEncryptionService enc;

    @BeforeEach
    void before() {
        keyService = new InMemoryKeyService();
        keyService.put("master", randomKey(AES_256_KEY_LEN));
        enc = new com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl(keyService);
    }

    @Test
    @DisplayName("加密/解密 roundtrip 使用独立 IV + GCM 认证（非 ECB）")
    void cryptoModeGcm() throws EncryptionException {
        String plain = "hello ECOS W37";
        String c1 = enc.encrypt(plain, "master");
        String c2 = enc.encrypt(plain, "master");
        assertNotEquals(c1, c2, "同一明文两次加密应不同（随机 IV 前置）");

        byte[] b64 = Base64.getDecoder().decode(c1);
        assertEquals(VERSION_GCM, b64[0], "密文首字节 = 版本 0x01");
        assertTrue(b64.length >= 1 + 12 + 16, "密文须覆盖 version + iv(12) + ciphertext + tag(16)");

        // 篡改 bit → GCM 应报认证失败（ECB 不会）
        byte[] tampered = b64.clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertThrows(EncryptionException.class,
                () -> enc.decrypt(new String(Base64.getEncoder().encode(tampered), StandardCharsets.UTF_8), "master"),
                "GCM 篡改必须解密失败（认证 tag）");

        assertEquals(plain, enc.decrypt(c1, "master"));
    }

    @Test
    @DisplayName("轮换：新 keyId 写入、旧 keyId 保持解密能力")
    void keyRotation() throws EncryptionException {
        // 老密钥写
        String oldEnc = enc.encrypt("legacy", "master");
        // 派生新 keyId 并写入
        keyService.put("master-v2", randomKey(AES_256_KEY_LEN));
        String newEnc = enc.encrypt("v2", "master-v2");
        assertEquals("v2", enc.decrypt(newEnc, "master-v2"));
        // 老 keyId 密文仍可用（未回填改造）
        assertEquals("legacy", enc.decrypt(oldEnc, "master"));
    }

    @Test
    @DisplayName("缺密钥 → KeyNotFoundException（不静默建钥）")
    void missingKeyDeny() {
        InMemoryKeyService noKey = new InMemoryKeyService();
        IDataEncryptionService e2 = new com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl(noKey);
        // directKeyBytes 空 → DataEncryptionServiceImpl.resolveKey → 抛 KeyNotFoundException
        Throwable t = assertThrows(EncryptionException.class, () -> e2.encrypt("x", "master"),
                "加解密链禁止静默建钥，缺钥必须抛");
        assertTrue(t.getMessage().contains("ECOS-SEC-430"), "错误码须为 ECOS-SEC-430；实际=" + t.getMessage());
    }

    @Test
    @DisplayName("密文不含 ECB 重复块特征（Kerckhoffs 敌对样例：三组相同明文，ECB 会输出同块）")
    void noEcbRepeatBlock() throws Exception {
        byte[] block = new byte[16];
        java.util.Arrays.fill(block, (byte) 'A'); // 同一明文块
        String c = enc.encrypt(new String(block, StandardCharsets.UTF_8), "master");
        byte[] decoded = Base64.getDecoder().decode(c);
        // GCM：version(1) + iv(12) + ciphertext(16) + tag(16) = 45 字节
        assertEquals(45, decoded.length, "GCM 密文总长应为 1+12+16+16=45，ECB 无 IV 会更短");
        byte[] iv = java.util.Arrays.copyOfRange(decoded, 1, 13);
        byte[] iv2Probe;
        byte[] secondBytes = Base64.getDecoder().decode(enc.encrypt(new String(block, StandardCharsets.UTF_8), "master"));
        iv2Probe = java.util.Arrays.copyOfRange(secondBytes, 1, 13);
        assertFalse(java.util.Arrays.equals(iv, iv2Probe), "两次 IV 必须不同（GCM 语义）");
    }

    // ── helpers ──────────────────────────────────────────

    private static byte[] randomKey(int len) {
        byte[] b = new byte[len];
        new java.security.SecureRandom().nextBytes(b);
        return b;
    }

    /** 内存版 IKeyManagementService：与生产 KeyManagementServiceImpl 隔离，测试可控。 */
    static final class InMemoryKeyService implements IKeyManagementService {
        private final Map<String, Key> map = new ConcurrentHashMap<>();

        void put(String id, byte[] bytes) { map.put(id, new javax.crypto.spec.SecretKeySpec(bytes, "AES")); }

        @Override public Key getKey(String keyId) { return map.get(keyId); }
        @Override public byte[] getKeyBytes(String keyId) throws KeyManagementException {
            Key k = map.get(keyId);
            return k == null ? null : k.getEncoded();
        }
        @Override public Key createKey(String keyId, String algorithm, int keySize) throws KeyManagementException {
            throw new KeyManagementException("生产环境运行期禁止 createKey (W37)");
        }
    }
}
