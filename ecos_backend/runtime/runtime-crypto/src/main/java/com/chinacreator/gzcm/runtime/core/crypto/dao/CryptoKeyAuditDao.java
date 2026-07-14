package com.chinacreator.gzcm.runtime.core.crypto.dao;

/**
 * 密钥操作审计DAO接口
 * 
 * @author CDRC Runtime Team
 */
public interface CryptoKeyAuditDao {
    
    /**
     * 记录操作日志
     * 
     * @param keyId 密钥ID
     * @param version 密钥版本
     * @param operationType 操作类型（CREATE/GET/DELETE/ROTATE）
     * @param operationResult 操作结果（SUCCESS/FAILURE）
     * @param operatorId 操作员ID
     * @param operatorIp 操作员IP
     * @param errorMessage 错误信息
     * @throws Exception
     */
    void logOperation(String keyId, Integer version, String operationType, 
                     String operationResult, String operatorId, String operatorIp, 
                     String errorMessage) throws Exception;
}

