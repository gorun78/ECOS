package com.chinacreator.gzcm.runtime.core.crypto;

import java.security.Key;

/**
 * 瀵嗛挜绠＄悊鏈嶅姟鎺ュ彛锛堝崰浣嶆帴鍙ｏ級
 * 寰呬换鍔?.3.1瀹屾垚鍚庡畬鍠?
 * 
 * @author CDRC Runtime Team
 */
public interface IKeyManagementService {
    
    /**
     * 鑾峰彇瀵嗛挜
     * 
     * @param keyId 瀵嗛挜ID
     * @return 瀵嗛挜瀵硅薄
     * @throws KeyManagementException
     */
    Key getKey(String keyId) throws KeyManagementException;
    
    /**
     * 鑾峰彇瀵嗛挜瀛楄妭鏁扮粍
     * 
     * @param keyId 瀵嗛挜ID
     * @return 瀵嗛挜瀛楄妭鏁扮粍
     * @throws KeyManagementException
     */
    byte[] getKeyBytes(String keyId) throws KeyManagementException;
    
    /**
     * 鍒涘缓瀵嗛挜
     * 
     * @param keyId 瀵嗛挜ID
     * @param algorithm 鍔犲瘑绠楁硶锛堝AES锛?
     * @param keySize 瀵嗛挜闀垮害锛堝256锛?
     * @return 瀵嗛挜瀵硅薄
     * @throws KeyManagementException
     */
    Key createKey(String keyId, String algorithm, int keySize) throws KeyManagementException;
    
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

