package com.chinacreator.gzcm.runtime.core.crypto.kms;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.core.crypto.kms.aws.AWSKMSAdapter;
import com.chinacreator.gzcm.runtime.core.crypto.kms.azure.AzureKeyVaultAdapter;
import com.chinacreator.gzcm.runtime.core.crypto.kms.vault.HashiCorpVaultAdapter;
import com.chinacreator.gzcm.sysman.kms.KMSAdapter;

/**
 * KMS工厂类
 * 根据配置创建相应的KMS适配器
 */
public class KMSFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(KMSFactory.class);
    
    /**
     * 创建KMS适配器
     * 
     * @param kmsType KMS类型（AWS, AZURE, VAULT）
     * @param config 配置信息
     * @return KMS适配器
     * @throws KMSException 创建失败
     */
    public static KMSAdapter createKMSAdapter(String kmsType, Map<String, Object> config) throws KMSAdapter.KMSException {
        if (kmsType == null || kmsType.trim().isEmpty()) {
            throw new KMSAdapter.KMSException("KMS类型不能为空");
        }
        
        try {
            String type = kmsType.toUpperCase();
            
            switch (type) {
                case "AWS":
                    return createAWSKMSAdapter(config);
                case "AZURE":
                    return createAzureKeyVaultAdapter(config);
                case "VAULT":
                case "HASHICORP":
                    return createHashiCorpVaultAdapter(config);
                default:
                    throw new KMSAdapter.KMSException("不支持的KMS类型: " + kmsType);
            }
        } catch (KMSAdapter.KMSException e) {
            throw e;
        } catch (Exception e) {
            throw new KMSAdapter.KMSException("创建KMS适配器失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建AWS KMS适配器
     */
    private static KMSAdapter createAWSKMSAdapter(Map<String, Object> config) {
        String region = (String) config.get("region");
        String accessKeyId = (String) config.get("accessKeyId");
        String secretAccessKey = (String) config.get("secretAccessKey");
        
        if (region == null || region.trim().isEmpty()) {
            throw new IllegalArgumentException("AWS区域不能为空");
        }
        
        logger.info("创建AWS KMS适配器: region={}", region);
        return new AWSKMSAdapter(region, accessKeyId, secretAccessKey);
    }
    
    /**
     * 创建Azure Key Vault适配器
     */
    private static KMSAdapter createAzureKeyVaultAdapter(Map<String, Object> config) {
        String vaultUrl = (String) config.get("vaultUrl");
        String clientId = (String) config.get("clientId");
        String clientSecret = (String) config.get("clientSecret");
        String tenantId = (String) config.get("tenantId");
        
        if (vaultUrl == null || vaultUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Azure Key Vault URL不能为空");
        }
        
        logger.info("创建Azure Key Vault适配器: vaultUrl={}", vaultUrl);
        return new AzureKeyVaultAdapter(vaultUrl, clientId, clientSecret, tenantId);
    }
    
    /**
     * 创建HashiCorp Vault适配器
     */
    private static KMSAdapter createHashiCorpVaultAdapter(Map<String, Object> config) {
        String vaultAddress = (String) config.get("vaultAddress");
        String vaultToken = (String) config.get("vaultToken");
        String enginePath = (String) config.get("enginePath");
        
        if (vaultAddress == null || vaultAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Vault地址不能为空");
        }
        
        logger.info("创建HashiCorp Vault适配器: vaultAddress={}", vaultAddress);
        return new HashiCorpVaultAdapter(vaultAddress, vaultToken, enginePath);
    }
}
