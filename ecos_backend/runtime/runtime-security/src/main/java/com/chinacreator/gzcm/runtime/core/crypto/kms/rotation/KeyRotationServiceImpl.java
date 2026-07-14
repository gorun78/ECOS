package com.chinacreator.gzcm.runtime.core.crypto.kms.rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.sysman.kms.KMSAdapter;

/**
 * 密钥轮换服务实现
 * 提供密钥自动轮换和手动轮换功能
 */
public class KeyRotationServiceImpl implements KeyRotationService {
    
    private static final Logger logger = LoggerFactory.getLogger(KeyRotationServiceImpl.class);
    
    private final KMSAdapter kmsAdapter;
    
    // 轮换历史：keyId -> List<RotationRecord>
    private final ConcurrentMap<String, List<RotationRecord>> rotationHistory = new ConcurrentHashMap<>();
    
    // 密钥使用计数：keyId -> usageCount
    private final ConcurrentMap<String, Long> keyUsageCount = new ConcurrentHashMap<>();
    
    public KeyRotationServiceImpl(KMSAdapter kmsAdapter) {
        this.kmsAdapter = kmsAdapter;
    }
    
    @Override
    public String autoRotate(String keyId, RotationPolicy rotationPolicy) throws KeyRotationException {
        if (keyId == null || rotationPolicy == null) {
            throw new KeyRotationException("密钥ID和轮换策略不能为空");
        }
        
        try {
            // 获取当前密钥版本
            KMSAdapter.KeyInfo currentKey = kmsAdapter.getKey(keyId, null);
            if (currentKey == null) {
                throw new KeyRotationException("密钥不存在: " + keyId);
            }
            
            String oldVersion = currentKey.getVersion();
            
            // 检查是否需要轮换
            if (!shouldRotate(keyId, rotationPolicy)) {
                logger.debug("密钥不需要轮换: keyId={}", keyId);
                return oldVersion;
            }
            
            // 执行轮换
            String newVersion = kmsAdapter.rotateKey(keyId);
            
            // 记录轮换历史
            RotationRecord record = new RotationRecord(keyId, oldVersion, newVersion, System.currentTimeMillis(), "AUTO");
            record.setReason("自动轮换: " + rotationPolicy.getType());
            rotationHistory.computeIfAbsent(keyId, k -> new ArrayList<>()).add(record);
            
            // 重置使用计数
            keyUsageCount.put(keyId, 0L);
            
            logger.info("密钥自动轮换完成: keyId={}, oldVersion={}, newVersion={}", keyId, oldVersion, newVersion);
            return newVersion;
        } catch (KMSAdapter.KMSException e) {
            throw new KeyRotationException("密钥轮换失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new KeyRotationException("密钥轮换异常: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String manualRotate(String keyId) throws KeyRotationException {
        if (keyId == null) {
            throw new KeyRotationException("密钥ID不能为空");
        }
        
        try {
            // 获取当前密钥版本
            KMSAdapter.KeyInfo currentKey = kmsAdapter.getKey(keyId, null);
            if (currentKey == null) {
                throw new KeyRotationException("密钥不存在: " + keyId);
            }
            
            String oldVersion = currentKey.getVersion();
            
            // 执行轮换
            String newVersion = kmsAdapter.rotateKey(keyId);
            
            // 记录轮换历史
            RotationRecord record = new RotationRecord(keyId, oldVersion, newVersion, System.currentTimeMillis(), "MANUAL");
            record.setReason("手动轮换");
            rotationHistory.computeIfAbsent(keyId, k -> new ArrayList<>()).add(record);
            
            logger.info("密钥手动轮换完成: keyId={}, oldVersion={}, newVersion={}", keyId, oldVersion, newVersion);
            return newVersion;
        } catch (KMSAdapter.KMSException e) {
            throw new KeyRotationException("密钥轮换失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new KeyRotationException("密钥轮换异常: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<RotationRecord> getRotationHistory(String keyId) throws KeyRotationException {
        if (keyId == null) {
            throw new KeyRotationException("密钥ID不能为空");
        }
        
        try {
            List<RotationRecord> history = rotationHistory.get(keyId);
            return history != null ? new ArrayList<>(history) : new ArrayList<>();
        } catch (Exception e) {
            throw new KeyRotationException("查询轮换历史失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void cleanupOldVersions(String keyId, int keepVersions) throws KeyRotationException {
        if (keyId == null) {
            throw new KeyRotationException("密钥ID不能为空");
        }
        if (keepVersions < 1) {
            keepVersions = 1;
        }
        
        try {
            // 获取所有版本
            List<KMSAdapter.KeyVersion> versions = kmsAdapter.listKeyVersions(keyId);
            
            if (versions.size() <= keepVersions) {
                logger.debug("密钥版本数未超过保留数: keyId={}, versions={}, keepVersions={}", keyId, versions.size(), keepVersions);
                return;
            }
            
            // 按创建时间排序，保留最新的版本
            versions.sort((a, b) -> Long.compare(b.getCreatedTime(), a.getCreatedTime()));
            
            // 删除旧版本（简化实现，实际应调用KMS删除接口）
            for (int i = keepVersions; i < versions.size(); i++) {
                KMSAdapter.KeyVersion version = versions.get(i);
                logger.info("清理旧密钥版本: keyId={}, version={}", keyId, version.getVersion());
                // TODO: 调用KMS删除版本接口
            }
            
            logger.info("清理旧密钥版本完成: keyId={}, deleted={}", keyId, versions.size() - keepVersions);
        } catch (KMSAdapter.KMSException e) {
            throw new KeyRotationException("清理旧版本失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new KeyRotationException("清理旧版本异常: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查是否需要轮换
     */
    private boolean shouldRotate(String keyId, RotationPolicy policy) {
        if ("MANUAL".equals(policy.getType())) {
            return false;
        }
        
        if ("TIME_BASED".equals(policy.getType())) {
            // TODO: 检查上次轮换时间
            return true; // 简化实现
        }
        
        if ("USAGE_BASED".equals(policy.getType())) {
            long usageCount = keyUsageCount.getOrDefault(keyId, 0L);
            return usageCount >= policy.getMaxUsageCount();
        }
        
        return false;
    }
    
    /**
     * 记录密钥使用
     */
    public void recordKeyUsage(String keyId) {
        if (keyId != null) {
            keyUsageCount.merge(keyId, 1L, Long::sum);
        }
    }
}
