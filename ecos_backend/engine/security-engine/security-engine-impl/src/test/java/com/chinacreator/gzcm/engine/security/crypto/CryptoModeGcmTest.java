package com.chinacreator.gzcm.engine.security.crypto;

import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService.EncryptionException;
import com.chinacreator.gzcm.engine.security.crypto.impl.GcmAeadService;
import com.chinacreator.gzcm.engine.security.crypto.impl.GcmAeadService.AeadDecryptException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W37 P0 — AES-256-GCM 模式验证。
 *
 * <p>证明 {@link GcmAeadService} 是认证流（GCM）而非 ECB：
 * 同一明文两次加密产生不同密文（随机 IV），密文不呈现重复 16 字节块。
 * 同时验证缺 GCM v1 版本字节（旧 ECB 密文）解密被硬拒绝（fail-off，禁静默兼容）。</p>
 */
class CryptoModeGcmTest {

    private static final byte[] KEY = new byte[32];
    private static final String KEY_ID = "key1";
    private static final int IV_LEN = 12;
    private static final int GCM_TAG_BYTES = 16;

    static {
        Arrays.fill(KEY, (byte) 0x42);
    }

    private GcmAeadService newService() {
        return new GcmAeadService(KEY);
    }

    @Test
    @DisplayName("encrypt→decrypt 往返一致")
    void encryptDecrypt_roundTrip() throws Exception {
        GcmAeadService svc = newService();
        String plaintext = "hello world";

        String b64 = svc.encrypt(plaintext, KEY_ID);
        assertNotNull(b64);
        assertFalse(b64.equals(plaintext), "密文不应等于明文");

        String decrypted = svc.decrypt(b64, KEY_ID);
        assertEquals(plaintext, decrypted, "GCM 往返必须还原明文");
    }

    @Test
    @DisplayName("同一明文两次加密得到不同 Base64 密文（随机 IV，非 ECB）")
    void gcmNotEcb_samePlaintextGivesDifferentCiphertexts() throws Exception {
        GcmAeadService svc = newService();
        String plain = "same plaintext, same plaintext, repeated";

        String c1 = svc.encrypt(plain, KEY_ID);
        String c2 = svc.encrypt(plain, KEY_ID);
        assertNotNull(c1);
        assertNotNull(c2);
        assertFalse(c1.equals(c2),
                "GCM 使用随机 IV，同一明文两次加密的密文必须不同（ECB 会相同）");
    }

    @Test
    @DisplayName("密文首字节 = VERSION_V1 (0x01)")
    void gcmCiphertextHasPrefixedVersionByte() throws Exception {
        GcmAeadService svc = newService();
        String b64 = svc.encrypt("version check", KEY_ID);

        byte[] raw = Base64.getDecoder().decode(b64);
        assertNotNull(raw);
        assertTrue(raw.length >= 1, "密文不可为空");
        assertEquals(GcmAeadService.VERSION_V1, raw[0], "首字节必须是 GCM v1 版本标记 0x01");
    }

    @Test
    @DisplayName("重复明文加密后密文区无连续重复 16 字节块（ECB 特征）")
    void gcmCiphertextHasNoEcbRepeating16ByteBlocks() throws Exception {
        GcmAeadService svc = newService();
        // 同 16 字符块 × 4 = 64 字符；ECB 会把相同明文块加密成相同密文块
        String block = "ABCDEFGHIJKLmnoP";
        String plaintext = block + block + block + block;

        byte[] raw = Base64.getDecoder().decode(svc.encrypt(plaintext, KEY_ID));
        // 布局: version(1) | IV(12) | ciphertext | tag(16)
        int ctStart = 1 + IV_LEN;
        int totalCt = raw.length - ctStart;
        int bodyLen = totalCt - GCM_TAG_BYTES; // 去掉 tag 后的明文对齐密文段
        assertTrue(bodyLen >= 32, "密文长度不足以容纳两个 16B 块");

        int firstBlockStart = ctStart;
        boolean allIdentical = true;
        for (int start = firstBlockStart + 16; start + 16 <= firstBlockStart + bodyLen; start += 16) {
            if (!arrayRegionEquals(raw, firstBlockStart, raw, start, 16)) {
                allIdentical = false;
                break;
            }
        }
        assertFalse(allIdentical,
                "GCM 认证流不应产生全部雷同的 16 字节块（这是 ECB 的特征）");
    }

    @Test
    @DisplayName("版本字节 ≠ 0x01 的旧 ECB 密文 → AeadDecryptException")
    void decryptRejectsEcbLegacyCiphertext() {
        GcmAeadService svc = newService();
        // 构造 version=0x00 的 fake blob（长度 >= 1+12+16 使长度校验通过，从而触发版本字节拒绝）
        byte[] legacy = new byte[1 + IV_LEN + GCM_TAG_BYTES + 4];
        Arrays.fill(legacy, (byte) 0x11);
        legacy[0] = 0x00; // 非 GCM v1
        String b64 = Base64.getEncoder().encodeToString(legacy);

        assertThrows(AeadDecryptException.class,
                () -> svc.decrypt(b64, KEY_ID),
                "非 GCM v1（存量 ECB）密文必须抛 AeadDecryptException，禁静默兼容");
    }

    @Test
    @DisplayName("长度 < 1+12+16 的过短密文 → AeadDecryptException")
    void decryptRejectsShortCiphertext() {
        GcmAeadService svc = newService();
        // 20 字节 < 29 字节最小可判读长度；即便首字节 = 0x01 也应因长度不足被拒
        byte[] shortBlob = new byte[20];
        Arrays.fill(shortBlob, (byte) 0x22);
        shortBlob[0] = GcmAeadService.VERSION_V1;

        assertThrows(AeadDecryptException.class,
                () -> svc.decrypt(shortBlob, KEY_ID),
                "过短密文（< 1+IV+TAG）必须抛 AeadDecryptException");
    }

    private static boolean arrayRegionEquals(byte[] a, int aOff, byte[] b, int bOff, int len) {
        for (int i = 0; i < len; i++) {
            if (a[aOff + i] != b[bOff + i]) {
                return false;
            }
        }
        return true;
    }
}
