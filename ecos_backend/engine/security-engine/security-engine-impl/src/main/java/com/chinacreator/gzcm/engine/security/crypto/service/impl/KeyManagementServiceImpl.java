package com.chinacreator.gzcm.engine.security.crypto.service.impl;

import java.security.Key;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService;
import com.chinacreator.gzcm.engine.security.crypto.kms.InMemoryKeyStore;
import com.chinacreator.gzcm.engine.security.crypto.kms.IKeyStore;

/**
 * IKeyManagementService 实现（B1 / 详细设计-01 W38 持久化改造）。
 *
 * <p>存储层由 {@link IKeyStore} 决定：
 * <ul>
 *   <li>零参构造（默认）→ {@link InMemoryKeyStore}，等价改造前 HashMap 语义，
 *       用于 {@code SecurityCryptoEgress} 的反射自持实例（standalone 无 Bean 部署态，
 *       与 H3-T-ARCH 迁移前 new 模式等价）。</li>
 *   <li>带参构造（{@code CryptoBeanConfig} 装配）→ 若 JdbcTemplate + ECOS_MASTER_KEY
 *       齐备则用 {@code JdbcKmsKeyStore}（PG 落库信封加密）；否则退回内存。</li>
 * </ul>
 * 保留自 {@code liveCache}：本 JVM 内刚 create 的 Key 对象直接命中（避免每次 wrap 新 SecretKeySpec）。</p>
 */
public class KeyManagementServiceImpl implements IKeyManagementService {

    private final IKeyStore store;
    private final Map<String, Key> liveCache = new HashMap<>();

    /** 零参构造：standalone 无 Spring 上下文的反思通道，等价迁移前 in-memory 行为。 */
    public KeyManagementServiceImpl() {
        this(new InMemoryKeyStore());
    }

    public KeyManagementServiceImpl(IKeyStore store) {
        this.store = store;
    }

    @Override
    public synchronized Key getKey(String keyId) throws KeyManagementException {
        Key k = liveCache.get(keyId);
        if (k != null) {
            return k;
        }
        byte[] raw = store.load(keyId);
        if (raw == null || raw.length == 0) {
            return null;
        }
        k = new SecretKeySpec(raw, "AES");
        liveCache.put(keyId, k);
        return k;
    }

    @Override
    public byte[] getKeyBytes(String keyId) throws KeyManagementException {
        Key k = getKey(keyId);
        return k == null ? null : k.getEncoded();
    }

    @Override
    public synchronized Key createKey(String keyId, String algorithm, int keySize) throws KeyManagementException {
        try {
            String algo = algorithm == null ? "AES" : algorithm;
            KeyGenerator generator = KeyGenerator.getInstance(algo);
            if (keySize > 0) {
                generator.init(keySize);
            }
            SecretKey key = (SecretKey) generator.generateKey();
            byte[] raw = key.getEncoded();
            store.save(keyId, raw);
            liveCache.put(keyId, key);
            return key;
        } catch (Exception e) {
            throw new KeyManagementException("Failed to create key", e);
        }
    }
}
