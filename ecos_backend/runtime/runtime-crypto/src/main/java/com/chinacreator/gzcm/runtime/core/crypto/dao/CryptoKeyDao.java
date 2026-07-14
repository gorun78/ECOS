package com.chinacreator.gzcm.runtime.core.crypto.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.crypto.model.KeyMetadata;

/**
 * 瀵嗛挜DAO鎺ュ彛
 * 
 * @author CDRC Runtime Team
 */
public interface CryptoKeyDao {
    
    /**
     * 鎻掑叆瀵嗛挜鍏冩暟鎹?
     */
    void insert(KeyMetadata metadata, String encryptedKeyContent) throws Exception;
    
    /**
     * 鏇存柊瀵嗛挜鍏冩暟鎹?
     */
    void update(KeyMetadata metadata) throws Exception;
    
    /**
     * 鏍规嵁瀵嗛挜ID鍜岀増鏈煡璇㈠瘑閽ュ厓鏁版嵁鍜屽姞瀵嗗唴瀹?
     */
    KeyMetadataWithContent findByIdAndVersion(String keyId, Integer version) throws Exception;
    
    /**
     * 鏌ヨ瀵嗛挜鐨勬渶鏂扮増鏈?
     */
    KeyMetadataWithContent findLatestVersion(String keyId) throws Exception;
    
    /**
     * 鏌ヨ瀵嗛挜鐨勬墍鏈夌増鏈?
     */
    List<KeyMetadata> findVersions(String keyId) throws Exception;
    
    /**
     * 鏍规嵁鏉′欢鏌ヨ瀵嗛挜鍒楄〃
     */
    List<KeyMetadata> query(Map<String, Object> condition) throws Exception;
    
    /**
     * 杞垹闄ゅ瘑閽ワ紙鏍囪涓篋ELETED锛?
     */
    void delete(String keyId, Integer version) throws Exception;
    
    /**
     * 鏌ヨ闇€瑕佽疆鎹㈢殑瀵嗛挜
     */
    List<KeyMetadata> findKeysForRotation() throws Exception;
    
    /**
     * 瀵嗛挜鍏冩暟鎹拰鍔犲瘑鍐呭
     */
    class KeyMetadataWithContent {
        private KeyMetadata metadata;
        private String encryptedKeyContent;
        
        public KeyMetadataWithContent(KeyMetadata metadata, String encryptedKeyContent) {
            this.metadata = metadata;
            this.encryptedKeyContent = encryptedKeyContent;
        }
        
        public KeyMetadata getMetadata() {
            return metadata;
        }
        
        public String getEncryptedKeyContent() {
            return encryptedKeyContent;
        }
    }
}

