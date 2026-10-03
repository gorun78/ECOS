package com.chinacreator.gzcm.engine.security.crypto.impl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService.EncryptionException;
import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService;

/**
 * 数据加密服务实现。
 *
 * <p>详细设计-01 W37（P0）改造：
 * <ul>
 *   <li>算法 = <b>AES-256-GCM</b>（随机 IV 前置，逐行独立）—— 替代存量
 *       {@code Cipher.getInstance("AES")}（ECB，重复明文 = 重复密文块）；</li>
 *   <li>密钥来源 = {@link IKeyManagementService}（KMS 委托）或 {@code ECOS_MASTER_KEY}
 *       env（Base64 32B）；<b>禁"不存在即自动创建"分支</b>：缺钥 →
 *       {@link GcmAeadService.KeyNotFound}（映射 500 ECOS-SEC-430），写操作失败不静默生成；</li>
 *   <li>存量 ECB 密文解密失败 → {@link GcmAeadService.AeadDecryptException}
 *       （存量在线重加密属待用户裁决项 ③，禁静默兼容改写）。</li>
 * </ul></p>
 */
public class DataEncryptionServiceImpl implements IDataEncryptionService {

    private final IKeyManagementService keyService;
    private final GcmAeadService gcm;

    public DataEncryptionServiceImpl(IKeyManagementService keyService) {
        this.keyService = keyService;
        this.gcm = new GcmAeadService(resolveMasterKeyFromEnv());
    }

    /** 诊断/测试：主密钥是否已注入 */
    public boolean isKeyConfigured(String keyId) {
        try {
            if (gcm.isKeyConfigured()) return true;
            byte[] kb = keyService != null ? keyService.getKeyBytes(keyId) : null;
            return kb != null && kb.length >= 16;
        } catch (IKeyManagementService.KeyManagementException e) {
            return false;
        }
    }

    /** 委托链路取 32B 主密钥（KMS KeyManagementService → env）；拿不到 → null（使用时缺钥拒绝）。 */
    private byte[] resolveMasterKey(String keyId) {
        try {
            byte[] kb = keyService != null ? keyService.getKeyBytes(keyId) : null;
            if (kb != null && kb.length >= 32) {
                return kb.length == 32 ? kb : expandTo32(kb);
            }
        } catch (IKeyManagementService.KeyManagementException e) {
            // KMS 侧缺失 → 继续 env 兜底
        }
        byte[] envKey = resolveMasterKeyFromEnv();
        return envKey != null ? envKey : (kb32Safe(keyId));
    }

    private byte[] kb32Safe(String keyId) {
        try {
            byte[] kb = keyService != null ? keyService.getKeyBytes(keyId) : null;
            if (kb != null && kb.length >= 32) {
                return kb.length == 32 ? kb : expandTo32(kb);
            }
        } catch (IKeyManagementService.KeyManagementException ignored) {
            // fall through
        }
        return null;
    }

    private static byte[] expandTo32(byte[] kb) {
        // 短密钥不支持：GCM AES-256 必须 32B（不足 → KMS/配置错误，按缺钥处理）
        return kb.length == 32 ? kb : null;
    }

    private static byte[] resolveMasterKeyFromEnv() {
        String env = System.getenv("ECOS_MASTER_KEY");
        if (env == null || env.isBlank()) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(env.trim());
            return decoded.length == 32 ? decoded : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String encrypt(String data, String keyId) throws EncryptionException {
        byte[] key = resolveMasterKey(keyId);
        if (key == null) {
            throw new EncryptionException(GcmAeadService.ERROR_CODE_MASTER_KEY_MISSING,
                    new GcmAeadService.KeyNotFound(keyId).getMessage(), new GcmAeadService.KeyNotFound(keyId));
        }
        try {
            byte[] out = new GcmAeadService(key).encrypt(data.getBytes(StandardCharsets.UTF_8), keyId);
            return Base64.getEncoder().encodeToString(out);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("ENC", "encrypt failed", e);
        }
    }

    @Override
    public String decrypt(String encryptedData, String keyId) throws EncryptionException {
        byte[] key = resolveMasterKey(keyId);
        if (key == null) {
            throw new EncryptionException(GcmAeadService.ERROR_CODE_MASTER_KEY_MISSING,
                    new GcmAeadService.KeyNotFound(keyId).getMessage(), new GcmAeadService.KeyNotFound(keyId));
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedData);
            byte[] plain = new GcmAeadService(key).decrypt(decoded, keyId);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("DEC", "decrypt failed", e);
        }
    }

    @Override
    public byte[] encrypt(byte[] data, String keyId) throws EncryptionException {
        byte[] key = resolveMasterKey(keyId);
        if (key == null) {
            throw new EncryptionException(GcmAeadService.ERROR_CODE_MASTER_KEY_MISSING,
                    new GcmAeadService.KeyNotFound(keyId).getMessage(), new GcmAeadService.KeyNotFound(keyId));
        }
        return new GcmAeadService(key).encrypt(data, keyId);
    }

    @Override
    public byte[] decrypt(byte[] encryptedData, String keyId) throws EncryptionException {
        byte[] key = resolveMasterKey(keyId);
        if (key == null) {
            throw new EncryptionException(GcmAeadService.ERROR_CODE_MASTER_KEY_MISSING,
                    new GcmAeadService.KeyNotFound(keyId).getMessage(), new GcmAeadService.KeyNotFound(keyId));
        }
        return new GcmAeadService(key).decrypt(encryptedData, keyId);
    }

    /** 字段级：String 字段按列名绑定 keyId，整体 GCM 加/解密（非字符串透传）。 */
    @Override
    public <T> T encryptField(T data, String fieldName, String keyId) throws EncryptionException {
        if (data == null) return data;
        if (data instanceof String s) {
            return (T) encrypt(s, keyId);
        }
        return data;
    }

    @Override
    public <T> T decryptField(T data, String fieldName, String keyId) throws EncryptionException {
        if (data == null) return data;
        if (data instanceof String s) {
            return (T) decrypt(s, keyId);
        }
        return data;
    }

    @Override
    public List<String> encryptBatch(List<String> dataList, String keyId) throws EncryptionException {
        return dataList.stream().map(d -> {
            try {
                return encrypt(d, keyId);
            } catch (EncryptionException e) {
                return null;
            }
        }).collect(Collectors.toList());
    }

    @Override
    public List<String> decryptBatch(List<String> encryptedDataList, String keyId) throws EncryptionException {
        return encryptedDataList.stream().map(d -> {
            try {
                return decrypt(d, keyId);
            } catch (EncryptionException e) {
                return null;
            }
        }).collect(Collectors.toList());
    }
}
