package com.chinacreator.gzcm.engine.security.crypto.impl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService;
import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService.KeyNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 详细设计-01 W37（P0）改造：
 * <ul>
 *   <li>AES-256-GCM 替代旧 {@code Cipher.getInstance("AES")}（=AES/ECB，无 IV）</li>
 *   <li>密文形态 {@code base64(version(1B) | IV(12B) | ciphertext | tag(16B))}
       version=0x01 = 当前 GCM 版本</li>
 *   <li>IV 每行独立 12B 随机（GCM 推荐长度）</li>
 *   <li>缺主密钥 → <b>抛 {@link KeyNotFoundException}</b>，端点映射 500 {@code ECOS-SEC-430}
 *       （<b>绝不静默建钥</b>，替代旧 {@code cipher()} 内 createKey 分支）</li>
 * </ul>
 */
public class DataEncryptionServiceImpl implements IDataEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(DataEncryptionServiceImpl.class);

    /** GCM 建议 IV 长度（NIST SP 800-38D）。 */
    private static final int GCM_IV_LEN = 12;
    /** GCM 认证 tag 长度（128 bit）。 */
    private static final int GCM_TAG_BITS = 128;
    /** 当前密文版本号。 */
    private static final byte VERSION_GCM = 0x01;

    private final IKeyManagementService keyService;
    private final SecureRandom random = new SecureRandom();

    public DataEncryptionServiceImpl(IKeyManagementService keyService) {
        this.keyService = keyService;
    }

    /**
     * W37：主密钥缺失 → 抛 {@link KeyNotFoundException}，<b>不自动建钥</b>。
     * 参数 {@code keyId} 当成逻辑名；未显式指定时回落默认主密钥 id（env 主密钥）。
     */
    private byte[] resolveKey(String keyId) throws KeyNotFoundException {
        String id = (keyId == null || keyId.isBlank())
                ? IKeyManagementService.DEFAULT_KEY_ID : keyId;
        try {
            byte[] keyBytes = keyService.getKeyBytes(id);
            if (keyBytes == null) {
                throw new KeyNotFoundException(id);
            }
            return keyBytes;
        } catch (KeyNotFoundException e) {
            throw e;
        } catch (Exception e) {
            // 兼容 KeyManagementException 其它子类：一律按缺钥处理（W37 禁静默建钥）
            throw new KeyNotFoundException(id);
        }
    }

    private Cipher newCipher(int mode, byte[] key, byte[] iv) throws Exception {
        SecretKeySpec spec = new SecretKeySpec(key, "AES");
        GCMParameterSpec specGcm = new GCMParameterSpec(GCM_TAG_BITS, iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode, spec, specGcm);
        return c;
    }

    @Override
    public String encrypt(String data, String keyId) throws EncryptionException {
        try {
            return Base64.getEncoder().encodeToString(
                    encrypt(data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8), keyId));
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("ENC", "encrypt failed", e);
        }
    }

    @Override
    public String decrypt(String encryptedData, String keyId) throws EncryptionException {
        try {
            byte[] raw = Base64.getDecoder().decode(encryptedData);
            return new String(decrypt(raw, keyId), StandardCharsets.UTF_8);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("DEC", "decrypt failed", e);
        }
    }

    /**
     * 加密：输出 {@code version(1B) | iv(12B) | gcmOutput(ciphertext||tag)}。
     */
    @Override
    public byte[] encrypt(byte[] data, String keyId) throws EncryptionException {
        try {
            byte[] key = resolveKey(keyId);
            byte[] iv = new byte[GCM_IV_LEN];
            random.nextBytes(iv);
            byte[] out = newCipher(Cipher.ENCRYPT_MODE, key, iv).doFinal(data == null ? new byte[0] : data);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(VERSION_GCM);
            bos.write(iv);
            bos.write(out);
            return bos.toByteArray();
        } catch (KeyNotFoundException e) {
            throw new EncryptionException(e.getErrorCode(),
                    "加密失败（主密钥缺失，禁止静默建钥）: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new EncryptionException("ENC", "encrypt failed", e);
        }
    }

    /**
     * 解密：读 {@code version(1B) | iv(12B)}，剩余 = {@code ciphertext||tag}。
     */
    @Override
    public byte[] decrypt(byte[] encryptedData, String keyId) throws EncryptionException {
        try {
            byte[] key = resolveKey(keyId);
            if (encryptedData == null || encryptedData.length < 1 + GCM_IV_LEN + 1) {
                throw new EncryptionException("DEC", "密文长度非法（缺 version/iv）");
            }
            byte version = encryptedData[0];
            if (version != VERSION_GCM) {
                // W37 分册-01 待裁决项③：ECB 存量密文是否在线重加密，暂按显式拒绝（对接 legacy）
                log.warn("未知密文版本 v={}（ECB 存量？按 FAIL_CLOSED 拒绝解密）", version);
                throw new EncryptionException("DEC", "未知密文版本（ECB 存量未迁移），解密拒绝");
            }
            byte[] iv = new byte[GCM_IV_LEN];
            System.arraycopy(encryptedData, 1, iv, 0, GCM_IV_LEN);
            int restOff = 1 + GCM_IV_LEN;
            byte[] body = new byte[encryptedData.length - restOff];
            System.arraycopy(encryptedData, restOff, body, 0, body.length);
            return newCipher(Cipher.DECRYPT_MODE, key, iv).doFinal(body);
        } catch (KeyNotFoundException e) {
            throw new EncryptionException(e.getErrorCode(),
                    "解密失败（主密钥缺失，禁止静默建钥）: " + e.getMessage(), e);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("DEC", "decrypt failed", e);
        }
    }

    @Override
    public <T> T encryptField(T data, String fieldName, String keyId) throws EncryptionException {
        return data;
    }

    @Override
    public <T> T decryptField(T data, String fieldName, String keyId) throws EncryptionException {
        return data;
    }

    @Override
    public List<String> encryptBatch(List<String> dataList, String keyId) throws EncryptionException {
        List<String> out = new ArrayList<>(dataList.size());
        for (String d : dataList) out.add(encrypt(d, keyId));
        return out;
    }

    @Override
    public List<String> decryptBatch(List<String> encryptedDataList, String keyId) throws EncryptionException {
        List<String> out = new ArrayList<>(encryptedDataList.size());
        for (String d : encryptedDataList) out.add(decrypt(d, keyId));
        return out;
    }
}
