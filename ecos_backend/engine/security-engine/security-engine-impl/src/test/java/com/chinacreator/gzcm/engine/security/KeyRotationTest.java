package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService.EncryptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 详细设计-01 C.2.6 — 密钥轮换（W37 关联）。
 *
 * <p>设计："轮换时旧 version 保留解密能力 2 个迭代；解密失败计数进告警"。
 * 测试语义：老密钥写入 oldEnc，新密钥（不同 keyId）写入 newEnc；新密码下：oldEnc 仍可解密，
 * newEnc 也可解密。<b>不同 keyId = 新对称密钥 = 新 version</b>，生产端由
 * {@code ecos_security_crypto_key.key_version} 记录版本。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("密钥轮换（W37 关联 · C.2.6）")
class KeyRotationTest {

    private CryptoModeGcmTest.InMemoryKeyService keys;
    private IDataEncryptionService enc;

    @BeforeEach
    void setUp() {
        keys = new CryptoModeGcmTest.InMemoryKeyService();
        keys.put("k-v1", rand32());
        enc = new com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl(keys);
    }

    @Test
    @DisplayName("轮换到 k-v2：k-v2 写入生效，k-v1 密文保留解密能力（2 迭代窗）")
    void rotateOldVersionStillDecryptable() throws Exception {
        String oldEnc = enc.encrypt("legacy-content", "k-v1");
        // 触发 rotate：写新 keyId
        keys.put("k-v2", rand32());
        String newEnc = enc.encrypt("v2-content", "k-v2");

        assertEquals("v2-content", enc.decrypt(newEnc, "k-v2"), "新 keyId 写入生效");
        assertEquals("legacy-content", enc.decrypt(oldEnc, "k-v1"), "旧 keyId 保留解密");
        // 老密文用新 keyId 解不应误解（不同密钥）
        assertThrows(EncryptionException.class, () -> enc.decrypt(oldEnc, "k-v2"));
    }

    @Test
    @DisplayName("解老版本存量密文（v≠0x01）显式拒绝，进告警")
    void unknownVersionRejected() {
        // 首字节 = 0x00（非法版本）+ 12B 00 + 16B 00
        byte[] legacy = new byte[29];
        legacy[0] = 0x00;
        assertThrows(EncryptionException.class,
                () -> enc.decrypt(legacy, "k-v1"),
                "未知版本必须显式拒绝，不静默改型");
    }

    private static byte[] rand32() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return b;
    }
}
