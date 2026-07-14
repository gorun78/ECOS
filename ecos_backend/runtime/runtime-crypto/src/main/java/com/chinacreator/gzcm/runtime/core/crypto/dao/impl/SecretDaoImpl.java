package com.chinacreator.gzcm.runtime.core.crypto.dao.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.crypto.dao.SecretDao;
import com.chinacreator.gzcm.runtime.core.crypto.entity.Secret;
import com.chinacreator.gzcm.runtime.core.crypto.entity.SecretAccessLog;
import com.chinacreator.gzcm.runtime.core.crypto.entity.SecretShare;

/**
 * 机密DAO实现（内存存储）
 * 
 * @author CDRC Runtime Team
 */
public class SecretDaoImpl implements SecretDao {
    
    private final Map<String, Secret> secrets = new ConcurrentHashMap<>();
    private final Map<String, SecretShare> shares = new ConcurrentHashMap<>();
    private final Map<String, SecretAccessLog> accessLogs = new ConcurrentHashMap<>();
    
    @Override
    public void createSecret(Secret secret) throws Exception {
        if (secret == null || secret.getSecretId() == null) {
            throw new IllegalArgumentException("Secret and secretId cannot be null");
        }
        secrets.put(secret.getSecretId(), secret);
    }
    
    @Override
    public Secret getSecret(String secretId) throws Exception {
        if (secretId == null) {
            throw new IllegalArgumentException("Secret ID cannot be null");
        }
        return secrets.get(secretId);
    }
    
    @Override
    public void updateSecret(Secret secret) throws Exception {
        if (secret == null || secret.getSecretId() == null) {
            throw new IllegalArgumentException("Secret and secretId cannot be null");
        }
        if (!secrets.containsKey(secret.getSecretId())) {
            throw new IllegalArgumentException("Secret not found: " + secret.getSecretId());
        }
        secrets.put(secret.getSecretId(), secret);
    }
    
    @Override
    public void deleteSecret(String secretId) throws Exception {
        if (secretId == null) {
            throw new IllegalArgumentException("Secret ID cannot be null");
        }
        secrets.remove(secretId);
    }
    
    @Override
    public List<Secret> listSecrets(String secretType, String status) throws Exception {
        return secrets.values().stream()
            .filter(secret -> {
                if (secretType != null && !secretType.equals(secret.getSecretType())) {
                    return false;
                }
                if (status != null && !status.equals(secret.getStatus())) {
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public void logAccess(SecretAccessLog log) throws Exception {
        if (log == null) {
            throw new IllegalArgumentException("Access log cannot be null");
        }
        if (log.getLogId() == null) {
            // 如果没有设置logId，生成一个
            log.setLogId(java.util.UUID.randomUUID().toString());
        }
        accessLogs.put(log.getLogId(), log);
    }
    
    /**
     * 查询访问日志（扩展方法，不在接口中）
     */
    public List<SecretAccessLog> listAccessLogs(String secretId, String userId) {
        return accessLogs.values().stream()
            .filter(log -> {
                if (secretId != null && !secretId.equals(log.getSecretId())) {
                    return false;
                }
                if (userId != null && !userId.equals(log.getUserId())) {
                    return false;
                }
                return true;
            })
            .sorted((a, b) -> {
                if (a.getCreatedTime() == null || b.getCreatedTime() == null) {
                    return 0;
                }
                return b.getCreatedTime().compareTo(a.getCreatedTime()); // 降序
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public void createShare(SecretShare share) throws Exception {
        if (share == null || share.getShareId() == null) {
            throw new IllegalArgumentException("Share and shareId cannot be null");
        }
        shares.put(share.getShareId(), share);
    }
    
    @Override
    public List<SecretShare> listShares(String secretId) throws Exception {
        if (secretId == null) {
            return new ArrayList<>(shares.values());
        }
        return shares.values().stream()
            .filter(share -> secretId.equals(share.getSecretId()))
            .collect(Collectors.toList());
    }
    
    @Override
    public void revokeShare(String shareId) throws Exception {
        if (shareId == null) {
            throw new IllegalArgumentException("Share ID cannot be null");
        }
        shares.remove(shareId);
    }
}

