package com.chinacreator.gzcm.runtime.core.crypto.service.impl;

import java.security.Key;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import com.chinacreator.gzcm.runtime.core.crypto.KeyManagementService;
import com.chinacreator.gzcm.runtime.core.crypto.model.KeyMetadata;

/**
 * 完整的密钥管理服务实现
 * 支持密钥创建、查询、删除、轮换等功能
 * 
 * @author CDRC Runtime Team
 */
public class KeyManagementServiceFullImpl implements KeyManagementService {
    
    // 密钥存储：keyId -> version -> Key
    private final Map<String, Map<Integer, Key>> keyStore = new ConcurrentHashMap<>();
    
    // 密钥元数据存储：keyId -> version -> KeyMetadata
    private final Map<String, Map<Integer, KeyMetadata>> metadataStore = new ConcurrentHashMap<>();
    
    @Override
    public KeyMetadata createKey(String keyId, String keyType, String algorithm, int keySize, String createdBy) 
            throws KeyManagementException {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(algorithm != null ? algorithm : "AES");
            if (keySize > 0) {
                generator.init(keySize);
            }
            SecretKey key = generator.generateKey();
            
            // 获取下一个版本号
            int version = getNextVersion(keyId);
            
            // 存储密钥
            keyStore.computeIfAbsent(keyId, k -> new ConcurrentHashMap<>()).put(version, key);
            
            // 创建元数据
            KeyMetadata metadata = new KeyMetadata();
            metadata.setKeyId(keyId);
            metadata.setKeyType(keyType != null ? keyType : "DEK");
            metadata.setAlgorithm(algorithm != null ? algorithm : "AES");
            metadata.setKeySize(keySize > 0 ? keySize : 256);
            metadata.setVersion(version);
            metadata.setStatus("ACTIVE");
            metadata.setCreatedTime(new Date());
            metadata.setCreatedBy(createdBy);
            metadata.setUpdatedTime(new Date());
            metadata.setUpdatedBy(createdBy);
            
            // 存储元数据
            metadataStore.computeIfAbsent(keyId, k -> new ConcurrentHashMap<>()).put(version, metadata);
            
            return metadata;
        } catch (Exception e) {
            throw new KeyManagementException("Failed to create key: " + keyId, e);
        }
    }
    
    @Override
    public Key getKey(String keyId) throws KeyManagementException {
        return getKey(keyId, null);
    }
    
    @Override
    public Key getKey(String keyId, Integer version) throws KeyManagementException {
        Map<Integer, Key> versions = keyStore.get(keyId);
        if (versions == null || versions.isEmpty()) {
            throw new KeyManagementException("Key not found: " + keyId);
        }
        
        if (version == null) {
            // 返回最新版本
            version = versions.keySet().stream().max(Integer::compareTo).orElseThrow(
                () -> new KeyManagementException("No version found for key: " + keyId));
        }
        
        Key key = versions.get(version);
        if (key == null) {
            throw new KeyManagementException("Key version not found: " + keyId + " version " + version);
        }
        
        return key;
    }
    
    @Override
    public byte[] getKeyBytes(String keyId) throws KeyManagementException {
        Key key = getKey(keyId);
        return key.getEncoded();
    }
    
    @Override
    public KeyMetadata getKeyMetadata(String keyId) throws KeyManagementException {
        return getKeyVersion(keyId, null);
    }
    
    @Override
    public void deleteKey(String keyId, Integer version, String deletedBy) throws KeyManagementException {
        if (version == null) {
            // 删除所有版本（软删除）
            Map<Integer, KeyMetadata> versions = metadataStore.get(keyId);
            if (versions != null) {
                for (KeyMetadata metadata : versions.values()) {
                    metadata.setStatus("DELETED");
                    metadata.setUpdatedTime(new Date());
                    metadata.setUpdatedBy(deletedBy);
                }
            }
        } else {
            // 删除指定版本
            Map<Integer, KeyMetadata> versions = metadataStore.get(keyId);
            if (versions != null) {
                KeyMetadata metadata = versions.get(version);
                if (metadata != null) {
                    metadata.setStatus("DELETED");
                    metadata.setUpdatedTime(new Date());
                    metadata.setUpdatedBy(deletedBy);
                }
            }
        }
    }
    
    @Override
    public KeyMetadata rotateKey(String keyId, String rotatedBy) throws KeyManagementException {
        try {
            // 获取当前密钥元数据
            KeyMetadata currentMetadata = getKeyMetadata(keyId);
            if (currentMetadata == null) {
                throw new KeyManagementException("Key not found: " + keyId);
            }
            
            // 创建新版本的密钥
            KeyMetadata newMetadata = createKey(
                keyId,
                currentMetadata.getKeyType(),
                currentMetadata.getAlgorithm(),
                currentMetadata.getKeySize(),
                rotatedBy
            );
            
            // 更新当前密钥的轮换信息
            currentMetadata.setLastRotationTime(new Date());
            if (currentMetadata.getRotationPeriod() != null) {
                long nextRotation = System.currentTimeMillis() + 
                    (currentMetadata.getRotationPeriod() * 24L * 60L * 60L * 1000L);
                currentMetadata.setNextRotationTime(new Date(nextRotation));
            }
            currentMetadata.setUpdatedTime(new Date());
            currentMetadata.setUpdatedBy(rotatedBy);
            
            // 更新新密钥的轮换信息
            newMetadata.setLastRotationTime(new Date());
            if (currentMetadata.getRotationPeriod() != null) {
                long nextRotation = System.currentTimeMillis() + 
                    (currentMetadata.getRotationPeriod() * 24L * 60L * 60L * 1000L);
                newMetadata.setNextRotationTime(new Date(nextRotation));
            }
            newMetadata.setRotationPeriod(currentMetadata.getRotationPeriod());
            
            return newMetadata;
        } catch (KeyManagementException e) {
            throw e;
        } catch (Exception e) {
            throw new KeyManagementException("Failed to rotate key: " + keyId, e);
        }
    }
    
    @Override
    public List<KeyMetadata> getKeyVersions(String keyId) throws KeyManagementException {
        Map<Integer, KeyMetadata> versions = metadataStore.get(keyId);
        if (versions == null || versions.isEmpty()) {
            throw new KeyManagementException("Key not found: " + keyId);
        }
        
        return new ArrayList<>(versions.values());
    }
    
    @Override
    public KeyMetadata getKeyVersion(String keyId, Integer version) throws KeyManagementException {
        Map<Integer, KeyMetadata> versions = metadataStore.get(keyId);
        if (versions == null || versions.isEmpty()) {
            throw new KeyManagementException("Key not found: " + keyId);
        }
        
        if (version == null) {
            // 返回最新版本
            version = versions.keySet().stream().max(Integer::compareTo).orElseThrow(
                () -> new KeyManagementException("No version found for key: " + keyId));
        }
        
        KeyMetadata metadata = versions.get(version);
        if (metadata == null) {
            throw new KeyManagementException("Key version not found: " + keyId + " version " + version);
        }
        
        return metadata;
    }
    
    @Override
    public List<KeyMetadata> queryKeys(String keyType, String status) throws KeyManagementException {
        List<KeyMetadata> result = new ArrayList<>();
        
        for (Map<Integer, KeyMetadata> versions : metadataStore.values()) {
            for (KeyMetadata metadata : versions.values()) {
                boolean match = true;
                
                if (keyType != null && !keyType.equals(metadata.getKeyType())) {
                    match = false;
                }
                
                if (status != null && !status.equals(metadata.getStatus())) {
                    match = false;
                }
                
                if (match) {
                    result.add(metadata);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 获取下一个版本号
     */
    private int getNextVersion(String keyId) {
        Map<Integer, KeyMetadata> versions = metadataStore.get(keyId);
        if (versions == null || versions.isEmpty()) {
            return 1;
        }
        
        return versions.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
    }
}

