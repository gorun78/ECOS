package com.chinacreator.gzcm.runtime.core.crypto.service;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.crypto.entity.Secret;
import com.chinacreator.gzcm.runtime.core.crypto.entity.SecretShare;

/**
 * 鏈哄瘑鏈嶅姟鎺ュ彛
 * 
 * @author CDRC Runtime Team
 */
public interface ISecretService {
    
    /**
     * 鍒涘缓鏈哄瘑
     * 
     * @param secretName 鏈哄瘑鍚嶇О
     * @param secretType 鏈哄瘑绫诲瀷
     * @param secretValue 鏈哄瘑鍊硷紙鏄庢枃锛?
     * @param keyId 鍔犲瘑瀵嗛挜ID
     * @param description 鎻忚堪
     * @param createdBy 鍒涘缓鑰匢D
     * @return 鏈哄瘑瀹炰綋锛堜笉鍖呭惈鏈哄瘑鍊硷級
     * @throws SecretException
     */
    Secret createSecret(String secretName, String secretType, String secretValue, 
            String keyId, String description, String createdBy) throws SecretException;
    
    /**
     * 鑾峰彇鏈哄瘑锛堜粎鍏冩暟鎹紝涓嶅寘鍚満瀵嗗€硷級
     * 
     * @param secretId 鏈哄瘑ID
     * @return 鏈哄瘑瀹炰綋
     * @throws SecretException
     */
    Secret getSecret(String secretId) throws SecretException;
    
    /**
     * 鑾峰彇鏈哄瘑鍊硷紙闇€瑕佹潈闄愶級
     * 
     * @param secretId 鏈哄瘑ID
     * @param userId 鐢ㄦ埛ID锛堢敤浜庢潈闄愭鏌ワ級
     * @return 瑙ｅ瘑鍚庣殑鏈哄瘑鍊?
     * @throws SecretException
     */
    String getSecretValue(String secretId, String userId) throws SecretException;
    
    /**
     * 鏇存柊鏈哄瘑
     * 
     * @param secretId 鏈哄瘑ID
     * @param secretValue 鏂扮殑鏈哄瘑鍊硷紙鏄庢枃锛?
     * @param updatedBy 鏇存柊鑰匢D
     * @return 鏇存柊鍚庣殑鏈哄瘑瀹炰綋
     * @throws SecretException
     */
    Secret updateSecret(String secretId, String secretValue, String updatedBy) throws SecretException;
    
    /**
     * 鍒犻櫎鏈哄瘑
     * 
     * @param secretId 鏈哄瘑ID
     * @param userId 鐢ㄦ埛ID锛堢敤浜庢潈闄愭鏌ワ級
     * @throws SecretException
     */
    void deleteSecret(String secretId, String userId) throws SecretException;
    
    /**
     * 鏌ヨ鏈哄瘑鍒楄〃
     * 
     * @param secretType 鏈哄瘑绫诲瀷锛堝彲閫夛級
     * @param status 鐘舵€侊紙鍙€夛級
     * @return 鏈哄瘑鍒楄〃锛堜笉鍖呭惈鏈哄瘑鍊硷級
     * @throws SecretException
     */
    List<Secret> listSecrets(String secretType, String status) throws SecretException;
    
    /**
     * 鍏变韩鏈哄瘑
     * 
     * @param secretId 鏈哄瘑ID
     * @param sharedToUserId 鍏变韩缁欑殑鐢ㄦ埛ID
     * @param permission 鏉冮檺锛圧EAD, WRITE锛?
     * @param createdBy 鍒涘缓鑰匢D
     * @return 鍏变韩瀹炰綋
     * @throws SecretException
     */
    SecretShare shareSecret(String secretId, String sharedToUserId, 
            String permission, String createdBy) throws SecretException;
    
    /**
     * 鎾ら攢鏈哄瘑鍏变韩
     * 
     * @param shareId 鍏变韩ID
     * @throws SecretException
     */
    void revokeShare(String shareId) throws SecretException;
    
    /**
     * 杞崲鏈哄瘑
     * 
     * @param secretId 鏈哄瘑ID
     * @param newSecretValue 鏂扮殑鏈哄瘑鍊硷紙鏄庢枃锛?
     * @param userId 鐢ㄦ埛ID
     * @return 鏇存柊鍚庣殑鏈哄瘑瀹炰綋
     * @throws SecretException
     */
    Secret rotateSecret(String secretId, String newSecretValue, String userId) throws SecretException;
    
    /**
     * 鏈哄瘑寮傚父
     */
    class SecretException extends Exception {
        private static final long serialVersionUID = 1L;
        private String errorCode;
        
        public SecretException(String message) {
            super(message);
        }
        
        public SecretException(String message, Throwable cause) {
            super(message, cause);
        }
        
        public SecretException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public SecretException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
    }
}

