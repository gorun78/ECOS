package com.chinacreator.gzcm.runtime.core.crypto;

import java.util.List;
import java.util.Map;

/**
 * 鏁版嵁鍔犲瘑鏈嶅姟鎺ュ彛
 * 
 * @author CDRC Runtime Team
 */
public interface IDataEncryptionService {
    
    /**
     * 鍔犲瘑鏁版嵁
     * 
     * @param data 寰呭姞瀵嗙殑鏁版嵁
     * @param keyId 瀵嗛挜ID
     * @return 鍔犲瘑鍚庣殑鏁版嵁锛圔ase64缂栫爜锛?
     * @throws EncryptionException
     */
    String encrypt(String data, String keyId) throws EncryptionException;
    
    /**
     * 瑙ｅ瘑鏁版嵁
     * 
     * @param encryptedData 鍔犲瘑鐨勬暟鎹紙Base64缂栫爜锛?
     * @param keyId 瀵嗛挜ID
     * @return 瑙ｅ瘑鍚庣殑鏁版嵁
     * @throws EncryptionException
     */
    String decrypt(String encryptedData, String keyId) throws EncryptionException;
    
    /**
     * 鍔犲瘑瀛楄妭鏁扮粍
     * 
     * @param data 寰呭姞瀵嗙殑鏁版嵁
     * @param keyId 瀵嗛挜ID
     * @return 鍔犲瘑鍚庣殑鏁版嵁
     * @throws EncryptionException
     */
    byte[] encrypt(byte[] data, String keyId) throws EncryptionException;
    
    /**
     * 瑙ｅ瘑瀛楄妭鏁扮粍
     * 
     * @param encryptedData 鍔犲瘑鐨勬暟鎹?
     * @param keyId 瀵嗛挜ID
     * @return 瑙ｅ瘑鍚庣殑鏁版嵁
     * @throws EncryptionException
     */
    byte[] decrypt(byte[] encryptedData, String keyId) throws EncryptionException;
    
    /**
     * 鍔犲瘑瀵硅薄瀛楁
     * 
     * @param <T> 瀵硅薄绫诲瀷
     * @param data 瀵硅薄
     * @param fieldName 瀛楁鍚?
     * @param keyId 瀵嗛挜ID
     * @return 鍔犲瘑鍚庣殑瀵硅薄
     * @throws EncryptionException
     */
    <T> T encryptField(T data, String fieldName, String keyId) throws EncryptionException;
    
    /**
     * 瑙ｅ瘑瀵硅薄瀛楁
     * 
     * @param <T> 瀵硅薄绫诲瀷
     * @param data 瀵硅薄
     * @param fieldName 瀛楁鍚?
     * @param keyId 瀵嗛挜ID
     * @return 瑙ｅ瘑鍚庣殑瀵硅薄
     * @throws EncryptionException
     */
    <T> T decryptField(T data, String fieldName, String keyId) throws EncryptionException;
    
    /**
     * 鎵归噺鍔犲瘑
     * 
     * @param dataList 寰呭姞瀵嗙殑鏁版嵁鍒楄〃
     * @param keyId 瀵嗛挜ID
     * @return 鍔犲瘑鍚庣殑鏁版嵁鍒楄〃
     * @throws EncryptionException
     */
    List<String> encryptBatch(List<String> dataList, String keyId) throws EncryptionException;
    
    /**
     * 鎵归噺瑙ｅ瘑
     * 
     * @param encryptedDataList 鍔犲瘑鐨勬暟鎹垪琛?
     * @param keyId 瀵嗛挜ID
     * @return 瑙ｅ瘑鍚庣殑鏁版嵁鍒楄〃
     * @throws EncryptionException
     */
    List<String> decryptBatch(List<String> encryptedDataList, String keyId) throws EncryptionException;
    
    /**
     * 鍔犲瘑寮傚父
     */
    class EncryptionException extends Exception {
        private static final long serialVersionUID = 1L;
        private String errorCode;
        
        public EncryptionException(String message) {
            super(message);
        }
        
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
        
        public EncryptionException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public EncryptionException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
    }
}

