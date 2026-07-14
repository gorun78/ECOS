package com.chinacreator.gzcm.runtime.core.crypto.service.impl;

import java.security.Key;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import com.chinacreator.gzcm.runtime.core.crypto.IKeyManagementService;

/**
 * Minimal key management: generates and caches symmetric keys in memory.
 */
public class KeyManagementServiceImpl implements IKeyManagementService {

    private final Map<String, Key> keyCache = new HashMap<>();

    @Override
    public Key getKey(String keyId) throws KeyManagementException {
        return keyCache.get(keyId);
    }

    @Override
    public byte[] getKeyBytes(String keyId) throws KeyManagementException {
        Key key = keyCache.get(keyId);
        return key == null ? null : key.getEncoded();
    }

    @Override
    public Key createKey(String keyId, String algorithm, int keySize) throws KeyManagementException {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(algorithm == null ? "AES" : algorithm);
            if (keySize > 0) {
                generator.init(keySize);
            }
            SecretKey key = generator.generateKey();
            keyCache.put(keyId, key);
            return key;
        } catch (Exception e) {
            throw new KeyManagementException("Failed to create key", e);
        }
    }
}

