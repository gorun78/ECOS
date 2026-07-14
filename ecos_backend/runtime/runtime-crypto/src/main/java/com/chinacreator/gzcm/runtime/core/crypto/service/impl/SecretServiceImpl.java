package com.chinacreator.gzcm.runtime.core.crypto.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.crypto.IKeyManagementService;
import com.chinacreator.gzcm.runtime.core.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.runtime.core.crypto.dao.SecretDao;
import com.chinacreator.gzcm.runtime.core.crypto.dao.impl.SecretDaoImpl;
import com.chinacreator.gzcm.runtime.core.crypto.entity.Secret;
import com.chinacreator.gzcm.runtime.core.crypto.entity.SecretAccessLog;
import com.chinacreator.gzcm.runtime.core.crypto.entity.SecretShare;
import com.chinacreator.gzcm.runtime.core.crypto.service.ISecretService;

/**
 * 机密管理服务实现
 * 提供机密的创建、查询、更新、删除、共享等功能
 * 
 * @author CDRC Runtime Team
 */
public class SecretServiceImpl implements ISecretService {

    private final Map<String, Secret> secrets = new ConcurrentHashMap<>();
    private final Map<String, SecretShare> shares = new ConcurrentHashMap<>();
    private final IKeyManagementService keyService;
    private final IDataEncryptionService encryptionService;
    private final SecretDao secretDao;

    public SecretServiceImpl(IKeyManagementService keyService, IDataEncryptionService encryptionService) {
        this.keyService = keyService;
        this.encryptionService = encryptionService;
        this.secretDao = new SecretDaoImpl();
    }
    
    /**
     * 记录访问日志
     */
    private void logAccess(String secretId, String userId, String action, String result, String errorMessage) {
        try {
            SecretAccessLog log = new SecretAccessLog();
            log.setLogId(UUID.randomUUID().toString());
            log.setSecretId(secretId);
            log.setUserId(userId);
            log.setAction(action);
            log.setResult(result);
            log.setErrorMessage(errorMessage);
            log.setCreatedTime(new Date());
            // 简化实现：IP地址和User Agent从系统属性或环境变量获取，或使用默认值
            log.setIpAddress(System.getProperty("runtime.secret.access.ip", "127.0.0.1"));
            log.setUserAgent(System.getProperty("runtime.secret.access.userAgent", "Runtime-Service"));
            secretDao.logAccess(log);
        } catch (Exception e) {
            // 日志记录失败不应影响主流程，仅打印错误
            System.err.println("Failed to log secret access: " + e.getMessage());
        }
    }

    @Override
    public Secret createSecret(String secretName, String secretType, String secretValue, 
            String keyId, String description, String createdBy) throws SecretException {
        if (secretName == null || secretName.trim().isEmpty()) {
            throw new SecretException("SECRET_NAME_REQUIRED", "Secret name is required");
        }
        if (secretValue == null) {
            throw new SecretException("SECRET_VALUE_REQUIRED", "Secret value is required");
        }
        if (keyId == null) {
            keyId = "default-master-key";
        }

        try {
            // 加密机密值
            String encryptedValue = encryptionService.encrypt(secretValue, keyId);

            // 创建机密实体
            Secret secret = new Secret();
            secret.setSecretId(UUID.randomUUID().toString());
            secret.setSecretName(secretName);
            secret.setSecretType(secretType != null ? secretType : "PASSWORD");
            secret.setSecretValueEncrypted(encryptedValue);
            secret.setKeyId(keyId);
            secret.setDescription(description);
            secret.setCreatedTime(new Date());
            secret.setCreatedBy(createdBy);
            secret.setUpdatedTime(new Date());
            secret.setUpdatedBy(createdBy);
            secret.setStatus("ACTIVE");

            secrets.put(secret.getSecretId(), secret);
            
            // 记录访问日志
            logAccess(secret.getSecretId(), createdBy, "CREATE", "SUCCESS", null);
            
            return secret;
        } catch (Exception e) {
            // 记录失败日志
            if (createdBy != null) {
                logAccess(null, createdBy, "CREATE", "FAILED", e.getMessage());
            }
            throw new SecretException("CREATE_FAILED", "Failed to create secret", e);
        }
    }

    @Override
    public Secret getSecret(String secretId) throws SecretException {
        if (secretId == null) {
            throw new SecretException("SECRET_ID_REQUIRED", "Secret ID is required");
        }

        Secret secret = secrets.get(secretId);
        if (secret == null) {
            throw new SecretException("SECRET_NOT_FOUND", "Secret not found: " + secretId);
        }

        // 返回不包含机密值的副本
        Secret result = new Secret();
        result.setSecretId(secret.getSecretId());
        result.setSecretName(secret.getSecretName());
        result.setSecretType(secret.getSecretType());
        result.setKeyId(secret.getKeyId());
        result.setDescription(secret.getDescription());
        result.setCreatedTime(secret.getCreatedTime());
        result.setCreatedBy(secret.getCreatedBy());
        result.setUpdatedTime(secret.getUpdatedTime());
        result.setUpdatedBy(secret.getUpdatedBy());
        result.setStatus(secret.getStatus());
        result.setRemark(secret.getRemark());
        // 不设置secretValueEncrypted

        // 记录访问日志（简化实现，userId从方法参数获取，如果没有则使用默认值）
        logAccess(secretId, "system", "READ", "SUCCESS", null);

        return result;
    }

    @Override
    public String getSecretValue(String secretId, String userId) throws SecretException {
        if (secretId == null) {
            throw new SecretException("SECRET_ID_REQUIRED", "Secret ID is required");
        }
        if (userId == null) {
            throw new SecretException("USER_ID_REQUIRED", "User ID is required");
        }

        Secret secret = secrets.get(secretId);
        if (secret == null) {
            throw new SecretException("SECRET_NOT_FOUND", "Secret not found: " + secretId);
        }

        // 简化实现：不进行权限检查，直接返回解密后的值
        // 实际实现中应该检查用户是否有权限访问该机密
        try {
            String value = encryptionService.decrypt(secret.getSecretValueEncrypted(), secret.getKeyId());
            // 记录访问日志
            logAccess(secretId, userId, "READ", "SUCCESS", null);
            return value;
        } catch (Exception e) {
            // 记录失败日志
            logAccess(secretId, userId, "READ", "FAILED", e.getMessage());
            throw new SecretException("DECRYPT_FAILED", "Failed to decrypt secret", e);
        }
    }

    @Override
    public Secret updateSecret(String secretId, String secretValue, String updatedBy) throws SecretException {
        if (secretId == null) {
            throw new SecretException("SECRET_ID_REQUIRED", "Secret ID is required");
        }
        if (secretValue == null) {
            throw new SecretException("SECRET_VALUE_REQUIRED", "Secret value is required");
        }

        Secret secret = secrets.get(secretId);
        if (secret == null) {
            throw new SecretException("SECRET_NOT_FOUND", "Secret not found: " + secretId);
        }

        try {
            // 加密新的机密值
            String encryptedValue = encryptionService.encrypt(secretValue, secret.getKeyId());
            secret.setSecretValueEncrypted(encryptedValue);
            secret.setUpdatedTime(new Date());
            secret.setUpdatedBy(updatedBy);

            // 记录访问日志
            logAccess(secretId, updatedBy, "UPDATE", "SUCCESS", null);

            return secret;
        } catch (Exception e) {
            // 记录失败日志
            logAccess(secretId, updatedBy, "UPDATE", "FAILED", e.getMessage());
            throw new SecretException("UPDATE_FAILED", "Failed to update secret", e);
        }
    }

    @Override
    public void deleteSecret(String secretId, String userId) throws SecretException {
        if (secretId == null) {
            throw new SecretException("SECRET_ID_REQUIRED", "Secret ID is required");
        }

        Secret secret = secrets.get(secretId);
        if (secret == null) {
            throw new SecretException("SECRET_NOT_FOUND", "Secret not found: " + secretId);
        }

        // 软删除：设置状态为DELETED
        secret.setStatus("DELETED");
        secret.setUpdatedTime(new Date());
        secret.setUpdatedBy(userId);

        // 删除相关的共享
        shares.values().removeIf(share -> share.getSecretId().equals(secretId));
        
        // 记录访问日志
        logAccess(secretId, userId, "DELETE", "SUCCESS", null);
    }

    @Override
    public List<Secret> listSecrets(String secretType, String status) throws SecretException {
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
            .map(secret -> {
                // 返回不包含机密值的副本
                Secret result = new Secret();
                result.setSecretId(secret.getSecretId());
                result.setSecretName(secret.getSecretName());
                result.setSecretType(secret.getSecretType());
                result.setKeyId(secret.getKeyId());
                result.setDescription(secret.getDescription());
                result.setCreatedTime(secret.getCreatedTime());
                result.setCreatedBy(secret.getCreatedBy());
                result.setUpdatedTime(secret.getUpdatedTime());
                result.setUpdatedBy(secret.getUpdatedBy());
                result.setStatus(secret.getStatus());
                result.setRemark(secret.getRemark());
                return result;
            })
            .collect(Collectors.toList());
    }

    @Override
    public SecretShare shareSecret(String secretId, String sharedToUserId, 
            String permission, String createdBy) throws SecretException {
        if (secretId == null) {
            throw new SecretException("SECRET_ID_REQUIRED", "Secret ID is required");
        }
        if (sharedToUserId == null) {
            throw new SecretException("USER_ID_REQUIRED", "Shared to user ID is required");
        }
        if (permission == null) {
            permission = "READ";
        }

        Secret secret = secrets.get(secretId);
        if (secret == null) {
            throw new SecretException("SECRET_NOT_FOUND", "Secret not found: " + secretId);
        }

        SecretShare share = new SecretShare();
        share.setShareId(UUID.randomUUID().toString());
        share.setSecretId(secretId);
        share.setSharedToUserId(sharedToUserId);
        share.setPermission(permission);
        share.setCreatedTime(new Date());
        share.setCreatedBy(createdBy);
        share.setStatus("ACTIVE");

        shares.put(share.getShareId(), share);
        return share;
    }

    @Override
    public void revokeShare(String shareId) throws SecretException {
        if (shareId == null) {
            throw new SecretException("SHARE_ID_REQUIRED", "Share ID is required");
        }

        SecretShare share = shares.get(shareId);
        if (share == null) {
            throw new SecretException("SHARE_NOT_FOUND", "Share not found: " + shareId);
        }

        share.setStatus("REVOKED");
    }

    @Override
    public Secret rotateSecret(String secretId, String newSecretValue, String userId) throws SecretException {
        if (secretId == null) {
            throw new SecretException("SECRET_ID_REQUIRED", "Secret ID is required");
        }
        if (newSecretValue == null) {
            throw new SecretException("SECRET_VALUE_REQUIRED", "New secret value is required");
        }

        try {
            // 更新机密值
            Secret secret = updateSecret(secretId, newSecretValue, userId);
            // 记录轮换日志（ROTATE操作）
            logAccess(secretId, userId, "ROTATE", "SUCCESS", null);
            return secret;
        } catch (Exception e) {
            // 记录失败日志
            logAccess(secretId, userId, "ROTATE", "FAILED", e.getMessage());
            throw e;
        }
    }
}

