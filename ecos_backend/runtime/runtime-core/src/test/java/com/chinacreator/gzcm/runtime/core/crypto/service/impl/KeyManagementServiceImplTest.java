package com.chinacreator.gzcm.runtime.core.crypto.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.security.Key;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.chinacreator.gzcm.runtime.core.crypto.IKeyManagementService.KeyManagementException;

/**
 * KeyManagementServiceImpl 单元测试
 */
@DisplayName("密钥管理服务测试")
class KeyManagementServiceImplTest {

    private KeyManagementServiceImpl keyManagementService;

    @BeforeEach
    void setUp() {
        keyManagementService = new KeyManagementServiceImpl();
    }

    @Test
    @DisplayName("创建密钥")
    void testCreateKey() throws Exception {
        String keyId = "test-key-001";
        String algorithm = "AES";
        int keySize = 256;

        Key key = keyManagementService.createKey(keyId, algorithm, keySize);
        assertNotNull(key);
        assertEquals(algorithm, key.getAlgorithm());
    }

    @Test
    @DisplayName("获取密钥")
    void testGetKey() throws Exception {
        String keyId = "test-key-001";
        keyManagementService.createKey(keyId, "AES", 256);

        Key key = keyManagementService.getKey(keyId);
        assertNotNull(key);
        assertEquals("AES", key.getAlgorithm());
    }

    @Test
    @DisplayName("获取密钥字节数组")
    void testGetKeyBytes() throws Exception {
        String keyId = "test-key-001";
        keyManagementService.createKey(keyId, "AES", 256);

        byte[] keyBytes = keyManagementService.getKeyBytes(keyId);
        assertNotNull(keyBytes);
        assertTrue(keyBytes.length > 0);
    }

    @Test
    @DisplayName("获取不存在的密钥")
    void testGetNonExistentKey() throws Exception {
        Key key = keyManagementService.getKey("non-existent-key");
        assertNull(key);
    }

    @Test
    @DisplayName("创建多个密钥")
    void testCreateMultipleKeys() throws Exception {
        Key key1 = keyManagementService.createKey("key-001", "AES", 256);
        Key key2 = keyManagementService.createKey("key-002", "AES", 128);
        
        assertNotNull(key1);
        assertNotNull(key2);
        assertNotEquals(key1, key2);
        
        Key retrieved1 = keyManagementService.getKey("key-001");
        Key retrieved2 = keyManagementService.getKey("key-002");
        
        assertNotNull(retrieved1);
        assertNotNull(retrieved2);
    }

    @Test
    @DisplayName("使用默认密钥大小创建密钥")
    void testCreateKeyWithDefaultSize() throws Exception {
        String keyId = "test-key-default";
        Key key = keyManagementService.createKey(keyId, "AES", 0);
        assertNotNull(key);
    }
}

