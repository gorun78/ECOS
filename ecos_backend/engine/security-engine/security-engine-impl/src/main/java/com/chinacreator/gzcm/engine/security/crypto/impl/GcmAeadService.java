package com.chinacreator.gzcm.engine.security.crypto.impl;

import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService.EncryptionException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 详细设计-01 C.2.6 / W37（P0）— AES-256-GCM 字段级加解密。
 *
 * <p>替代存量 {@code Cipher.getInstance("AES")}（ECB 模式，重复明文必然重复密文块）。
 * 密文存储格式（MC 多库兼容，单 BLOB 列）：
 * <pre>Base64( version(1B=0x01) | IV(12B 随机) | ciphertext | GCM tag(16B, AES 输出内置) )</pre>
 * 旧 ECB 密文（version 字节 ≠ 0x01）解密失败 → 计 {@code decryptFailures}，
 * 不落原文（存量 ECB 数据重加密属待用户裁决项 ③，禁静默兼容改写）。</p>
 *
 * <p><b>禁自动建钥</b>：主密钥唯一来源 {@code ECOS_MASTER_KEY}（env，Base64=32B）/
 * KMS（master_key_ref 只存引用名）；缺钥 → {@code KeyNotFound}（ECOS-SEC-430, 500），
 * 写操作失败，不静默生成（C.2.6 "KeyManagementService 去掉'不存在即自动创建'分支"）。</p>
 */
public final class GcmAeadService {

    public static final int VERSION_V1 = 0x01;
    private static final int IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final SecureRandom RND = new SecureRandom();
    /** D.5 错误码：加密主密钥缺失（禁静默建钥） */
    public static final String ERROR_CODE_MASTER_KEY_MISSING = "ECOS-SEC-430";

    /** 缺钥异常（映射 500 ECOS-SEC-430） */
    public static final class KeyNotFound extends Exception {
        public KeyNotFound(String keyId) {
            super("加密主密钥缺失: keyId=" + keyId + "（禁自动建钥，经 ECOS_MASTER_KEY/KMS 注入）");
        }
    }

    private final byte[] masterKey;

    /**
     * @param masterKeyBytes 主密钥字节（32B）；null/空 = 未注入（使用时抛 KeyNotFound）
     */
    public GcmAeadService(byte[] masterKeyBytes) {
        this.masterKey = (masterKeyBytes != null && masterKeyBytes.length == KEY_BYTES) ? masterKeyBytes : null;
    }

    public boolean isKeyConfigured() {
        return masterKey != null;
    }

    public String encrypt(String plaintext, String keyId) throws EncryptionException {
        byte[] out = encrypt(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8), keyId);
        return Base64.getEncoder().encodeToString(out);
    }

    public byte[] encrypt(byte[] data, String keyId) throws EncryptionException {
        if (masterKey == null) {
            throw new EncryptionException(ERROR_CODE_MASTER_KEY_MISSING, "加密主密钥缺失，写操作拒绝执行", null);
        }
        try {
            byte[] iv = new byte[IV_LEN];
            RND.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(data);
            byte[] out = new byte[1 + IV_LEN + ct.length];
            out[0] = VERSION_V1;
            System.arraycopy(iv, 0, out, 1, IV_LEN);
            System.arraycopy(ct, 0, out, 1 + IV_LEN, ct.length);
            return out;
        } catch (Exception e) {
            throw new EncryptionException("ENC", "GCM 加密失败", e);
        }
    }

    public String decrypt(String b64, String keyId) throws EncryptionException {
        byte[] out = decrypt(Base64.getDecoder().decode(b64), keyId);
        return new String(out, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 旧 ECB 密文（version≠0x01）解密不可行 → AeadDecryptException（存量重加密待裁 ③，禁静默兼容）。 */
    public static final class AeadDecryptException extends EncryptionException {
        public AeadDecryptException(String message) {
            super("AEC", message);
        }
    }

    public byte[] decrypt(byte[] data, String keyId) throws EncryptionException {
        if (masterKey == null) {
            throw new EncryptionException(ERROR_CODE_MASTER_KEY_MISSING, "加密主密钥缺失，读操作拒绝执行", null);
        }
        if (data == null || data.length < 1 + IV_LEN + 16 || data[0] != VERSION_V1) {
            throw new AeadDecryptException("非 GCM v1 密文（可能为存量 ECB 数据，重加密属待裁项）");
        }
        try {
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(data, 1, iv, 0, IV_LEN);
            int ctLen = data.length - 1 - IV_LEN;
            byte[] ct = new byte[ctLen];
            System.arraycopy(data, 1 + IV_LEN, ct, 0, ctLen);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new AeadDecryptException("GCM 解密失败（tag 校验或存量 ECB 密文）");
        }
    }
}
