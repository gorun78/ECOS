package com.chinacreator.gzcm.engine.security.crypto.kms;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 内存密钥存储后端（默认；等价改造前 {@code HashMap<String,Key>} 语义）。
 *
 * <p>B1 未接入 PG（standalone boot / 引擎单例反思通道）时用它，保证行为与迁移前一致；
 * gateway fat-JAR 聚合部署态由 {@code CryptoBeanConfig} 换成 {@link JdbcKmsKeyStore}。</p>
 */
public final class InMemoryKeyStore implements IKeyStore {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    @Override
    public byte[] load(String keyId) {
        return store.get(keyId);
    }

    @Override
    public void save(String keyId, byte[] keyBytes) {
        byte[] copy = keyBytes == null ? null : keyBytes.clone();
        store.put(keyId, copy);
    }
}
