package com.chinacreator.gzcm.runtime.core.crypto;

import java.security.Key;
import java.util.List;

import com.chinacreator.gzcm.runtime.core.crypto.model.KeyMetadata;

/**
 * 瀵嗛挜绠＄悊鏈嶅姟鎺ュ彛
 * 
 * @author CDRC Runtime Team
 */
public interface KeyManagementService {
    
    /**
     * 鍒涘缓瀵嗛挜
     * 
     * @param keyId 瀵嗛挜ID
     * @param keyType 瀵嗛挜绫诲瀷锛圖EK/KEK锛?
     * @param algorithm 鍔犲瘑绠楁硶锛圓ES/RSA绛夛級
     * @param keySize 瀵嗛挜闀垮害锛堜綅锛?
     * @param createdBy 鍒涘缓鑰?
     * @return 瀵嗛挜鍏冩暟鎹?
     * @throws KeyManagementException
     */
    KeyMetadata createKey(String keyId, String keyType, String algorithm, int keySize, String createdBy) throws KeyManagementException;
    
    /**
     * 鑾峰彇瀵嗛挜锛堣繑鍥濳ey瀵硅薄锛?
     * 
     * @param keyId 瀵嗛挜ID
     * @return 瀵嗛挜瀵硅薄
     * @throws KeyManagementException
     */
    Key getKey(String keyId) throws KeyManagementException;
    
    /**
     * 鑾峰彇瀵嗛挜锛堟寚瀹氱増鏈級
     * 
     * @param keyId 瀵嗛挜ID
     * @param version 鐗堟湰鍙?
     * @return 瀵嗛挜瀵硅薄
     * @throws KeyManagementException
     */
    Key getKey(String keyId, Integer version) throws KeyManagementException;
    
    /**
     * 鑾峰彇瀵嗛挜瀛楄妭鏁扮粍
     * 
     * @param keyId 瀵嗛挜ID
     * @return 瀵嗛挜瀛楄妭鏁扮粍
     * @throws KeyManagementException
     */
    byte[] getKeyBytes(String keyId) throws KeyManagementException;
    
    /**
     * 鑾峰彇瀵嗛挜鍏冩暟鎹?
     * 
     * @param keyId 瀵嗛挜ID
     * @return 瀵嗛挜鍏冩暟鎹?
     * @throws KeyManagementException
     */
    KeyMetadata getKeyMetadata(String keyId) throws KeyManagementException;
    
    /**
     * 鍒犻櫎瀵嗛挜锛堣蒋鍒犻櫎锛?
     * 
     * @param keyId 瀵嗛挜ID
     * @param version 鐗堟湰鍙凤紙null琛ㄧず鍒犻櫎鎵€鏈夌増鏈級
     * @param deletedBy 鍒犻櫎鑰?
     * @throws KeyManagementException
     */
    void deleteKey(String keyId, Integer version, String deletedBy) throws KeyManagementException;
    
    /**
     * 杞崲瀵嗛挜
     * 
     * @param keyId 瀵嗛挜ID
     * @param rotatedBy 杞崲鑰?
     * @return 鏂扮増鏈殑瀵嗛挜鍏冩暟鎹?
     * @throws KeyManagementException
     */
    KeyMetadata rotateKey(String keyId, String rotatedBy) throws KeyManagementException;
    
    /**
     * 鑾峰彇瀵嗛挜鐗堟湰鍒楄〃
     * 
     * @param keyId 瀵嗛挜ID
     * @return 鐗堟湰鍒楄〃
     * @throws KeyManagementException
     */
    List<KeyMetadata> getKeyVersions(String keyId) throws KeyManagementException;
    
    /**
     * 鑾峰彇瀵嗛挜鐗堟湰
     * 
     * @param keyId 瀵嗛挜ID
     * @param version 鐗堟湰鍙?
     * @return 瀵嗛挜鍏冩暟鎹?
     * @throws KeyManagementException
     */
    KeyMetadata getKeyVersion(String keyId, Integer version) throws KeyManagementException;
    
    /**
     * 鏌ヨ瀵嗛挜鍒楄〃
     * 
     * @param keyType 瀵嗛挜绫诲瀷锛堝彲閫夛級
     * @param status 鐘舵€侊紙鍙€夛級
     * @return 瀵嗛挜鍒楄〃
     * @throws KeyManagementException
     */
    List<KeyMetadata> queryKeys(String keyType, String status) throws KeyManagementException;
    
    /**
     * 瀵嗛挜绠＄悊寮傚父
     */
    class KeyManagementException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public KeyManagementException(String message) {
            super(message);
        }
        
        public KeyManagementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

