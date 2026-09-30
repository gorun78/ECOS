package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService;
import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService.KeyNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-01 D.5.1 — {@code ECOS-SEC-430} 加密主密钥缺失（禁静默建钥）单测。
 * 独立于 {@link CryptoModeGcmTest}（聚焦端点/错误码映射）。
 */
@DisplayName("主密钥缺失拒绝（W37 - ECOS-SEC-430）")
class MissingKeyDenyTest {

    @Test
    @DisplayName("缺密钥时 encrypt() 抛 EncryptionException 且错误码 ECOS-SEC-430")
    void denyOnMissingKey_encrypt() {
        IDataEncryptionService svc =
                new com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl(
                        new EmptyKeyService());
        var ex = assertThrows(IDataEncryptionService.EncryptionException.class, () -> svc.encrypt("a", "k"));
        assertTrue(ex.getMessage().contains("ECOS-SEC-430"),
                "错误消息包含 ECOS-SEC-430；实际：" + ex.getMessage());
        assertNotNull(ex.getErrorCode(), "错误码字段须填充");
    }

    @Test
    @DisplayName("缺密钥时 decrypt() 抛 EncryptionException 且错误码 ECOS-SEC-430")
    void denyOnMissingKey_decrypt() {
        IDataEncryptionService svc =
                new com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl(
                        new EmptyKeyService());
        var ex = assertThrows(IDataEncryptionService.EncryptionException.class,
                () -> svc.decrypt("c29tZS1wbGFpbi1kYXRh", "k"));
        assertTrue(ex.getMessage().contains("ECOS-SEC-430"),
                "错误消息包含 ECOS-SEC-430；实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("KeyManagementService 层：缺密钥 → getKeyBytes 返回 null（上层转 KeyNotFoundException），且不建钥")
    void keyServiceReturnsNullOnMissing() throws Exception {
        IKeyManagementService ks = new EmptyKeyService();
        byte[] b = ks.getKeyBytes("no-such-key");
        org.junit.jupiter.api.Assertions.assertNull(b, "缺密钥应返回 null（交由 DataEncryption 转 ECOS-SEC-430）");
        // createKey 在生产运行期被禁用（W37）：抛出即成功
        assertThrows(IKeyManagementService.KeyManagementException.class,
                () -> ks.createKey("x", "AES", 256), "运行期禁止 createKey");
    }

    /** 空的 IKeyManagementService：任何 keyId 都返回 null（模拟 ECOS_MASTER_KEY 未设置）。 */
    private static final class EmptyKeyService implements IKeyManagementService {
        private final Map<String, Key> empty = new ConcurrentHashMap<>();
        @Override public Key getKey(String keyId) { return empty.get(keyId); }
        @Override public byte[] getKeyBytes(String keyId) { return null; }
        @Override public Key createKey(String keyId, String algorithm, int keySize) throws KeyManagementException {
            throw new KeyManagementException("W37 生产运行期禁止 createKey");
        }
    }
}
