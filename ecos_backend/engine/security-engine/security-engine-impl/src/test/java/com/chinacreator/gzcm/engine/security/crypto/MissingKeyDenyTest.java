package com.chinacreator.gzcm.engine.security.crypto;

import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService.EncryptionException;
import com.chinacreator.gzcm.engine.security.crypto.impl.GcmAeadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W37 / D.5 — 禁自动建钥：缺主密钥时加/解密一律拒绝（ECOS-SEC-430, 500）。
 *
 * <p>验证 {@link GcmAeadService} 在 {@code masterKey == null}（未注入 32B 主密钥）时：
 * encrypt/decrypt 抛 {@link EncryptionException} 且错误码 = ECOS-SEC-430，
 * 不静默生成密钥（C.2.6 "KeyManagementService 去掉'不存在即自动创建'分支"）。</p>
 */
class MissingKeyDenyTest {

    @Test
    @DisplayName("无密钥加密 → EncryptionException(ECOS-SEC-430)")
    void encryptWithNoKey_throwsMasterKeyMissing() {
        GcmAeadService svc = new GcmAeadService(null);
        assertFalse(svc.isKeyConfigured(), "前置：确认无密钥");

        EncryptionException ex = assertThrows(EncryptionException.class,
                () -> svc.encrypt("secret payload", "k1"),
                "缺主密钥加密必须抛 EncryptionException，禁静默建钥");
        assertNotNull(ex, "异常实例不可为空");
        assertEquals(GcmAeadService.ERROR_CODE_MASTER_KEY_MISSING, ex.getErrorCode(),
                "错误码必须 = ECOS-SEC-430");
        assertEquals("ECOS-SEC-430", ex.getErrorCode(), "错误码字面值必须 = ECOS-SEC-430");
    }

    @Test
    @DisplayName("无密钥解密 → EncryptionException(ECOS-SEC-430)")
    void decryptWithNoKey_throwsMasterKeyMissing() {
        GcmAeadService svc = new GcmAeadService(null);
        String b64 = java.util.Base64.getEncoder().encodeToString(new byte[]{0x01});

        EncryptionException ex = assertThrows(EncryptionException.class,
                () -> svc.decrypt(b64, "k1"),
                "缺主密钥解密必须抛 EncryptionException，读操作同样拒绝");
        assertNotNull(ex);
        assertEquals(GcmAeadService.ERROR_CODE_MASTER_KEY_MISSING, ex.getErrorCode(),
                "解密缺钥错误码必须 = ECOS-SEC-430");
        assertEquals("ECOS-SEC-430", ex.getErrorCode());
    }

    @Test
    @DisplayName("isKeyConfigured：(null) 主密钥 → false")
    void isKeyConfiguredFalseWhenNull() {
        GcmAeadService svc = new GcmAeadService(null);
        assertFalse(svc.isKeyConfigured(), "null 主密钥未配置");
    }

    @Test
    @DisplayName("isKeyConfigured：32B 主密钥 → true")
    void isKeyConfiguredTrueWhen32Bytes() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x7E);
        GcmAeadService svc = new GcmAeadService(key);
        assertTrue(svc.isKeyConfigured(), "32B 主密钥必须判定为已配置");
    }

    @Test
    @DisplayName("isKeyConfigured：错误长度(16B)主密钥 → false")
    void isKeyConfiguredFalseWhenWrongLength() {
        GcmAeadService svc = new GcmAeadService(new byte[16]);
        assertFalse(svc.isKeyConfigured(), "非 32B（AES-256）主密钥不可用，应判未配置");
        // 空数组同样未配置
        GcmAeadService emptySvc = new GcmAeadService(new byte[0]);
        assertFalse(emptySvc.isKeyConfigured(), "空主密钥不可用");
    }
}
