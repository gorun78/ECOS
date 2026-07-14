package com.chinacreator.gzcm.runtime.core.crypto.dao;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.crypto.entity.Secret;
import com.chinacreator.gzcm.runtime.core.crypto.entity.SecretAccessLog;
import com.chinacreator.gzcm.runtime.core.crypto.entity.SecretShare;

/**
 * 鏈哄瘑DAO鎺ュ彛
 * 
 * @author CDRC Runtime Team
 */
public interface SecretDao {
    
    /**
     * 鍒涘缓鏈哄瘑
     * 
     * @param secret 鏈哄瘑瀹炰綋
     * @throws Exception
     */
    void createSecret(Secret secret) throws Exception;
    
    /**
     * 鏍规嵁ID鏌ヨ鏈哄瘑
     * 
     * @param secretId 鏈哄瘑ID
     * @return 鏈哄瘑瀹炰綋
     * @throws Exception
     */
    Secret getSecret(String secretId) throws Exception;
    
    /**
     * 鏇存柊鏈哄瘑
     * 
     * @param secret 鏈哄瘑瀹炰綋
     * @throws Exception
     */
    void updateSecret(Secret secret) throws Exception;
    
    /**
     * 鍒犻櫎鏈哄瘑锛堣蒋鍒犻櫎锛?
     * 
     * @param secretId 鏈哄瘑ID
     * @throws Exception
     */
    void deleteSecret(String secretId) throws Exception;
    
    /**
     * 鏌ヨ鏈哄瘑鍒楄〃
     * 
     * @param secretType 鏈哄瘑绫诲瀷锛堝彲閫夛級
     * @param status 鐘舵€侊紙鍙€夛級
     * @return 鏈哄瘑鍒楄〃
     * @throws Exception
     */
    List<Secret> listSecrets(String secretType, String status) throws Exception;
    
    /**
     * 璁板綍璁块棶鏃ュ織
     * 
     * @param log 璁块棶鏃ュ織
     * @throws Exception
     */
    void logAccess(SecretAccessLog log) throws Exception;
    
    /**
     * 鍒涘缓鏈哄瘑鍏变韩
     * 
     * @param share 鍏变韩瀹炰綋
     * @throws Exception
     */
    void createShare(SecretShare share) throws Exception;
    
    /**
     * 鏌ヨ鏈哄瘑鍏变韩鍒楄〃
     * 
     * @param secretId 鏈哄瘑ID
     * @return 鍏变韩鍒楄〃
     * @throws Exception
     */
    List<SecretShare> listShares(String secretId) throws Exception;
    
    /**
     * 鎾ら攢鏈哄瘑鍏变韩
     * 
     * @param shareId 鍏变韩ID
     * @throws Exception
     */
    void revokeShare(String shareId) throws Exception;
}

