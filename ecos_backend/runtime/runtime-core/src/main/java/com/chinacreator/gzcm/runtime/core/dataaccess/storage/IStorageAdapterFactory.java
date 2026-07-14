package com.chinacreator.gzcm.runtime.core.dataaccess.storage;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.dataaccess.storage.enums.StorageType;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.StorageConfig;

/**
 * 瀛樺偍閫傞厤鍣ㄥ伐鍘傛帴鍙?
 * 璐熻矗鍒涘缓鍜岀鐞嗗瓨鍌ㄩ€傞厤鍣ㄥ疄渚?
 * 
 * @author CDRC Runtime Team
 */
public interface IStorageAdapterFactory {
    
    /**
     * 鍒涘缓瀛樺偍閫傞厤鍣?
     * 
     * @param storageType 瀛樺偍绫诲瀷
     * @param config 瀛樺偍閰嶇疆
     * @return 瀛樺偍閫傞厤鍣ㄥ疄渚?
     * @throws Exception
     */
    IStorageAdapter createAdapter(StorageType storageType, StorageConfig config) throws Exception;
    
    /**
     * 鍒涘缓瀛樺偍閫傞厤鍣紙浣跨敤瀛楃涓茬被鍨嬶紝淇濇寔鍚戝悗鍏煎锛?
     * 
     * @param storageType 瀛樺偍绫诲瀷锛圡YSQL, POSTGRESQL绛夛級
     * @param storageConfig 瀛樺偍閰嶇疆锛圝SON鏍煎紡瀛楃涓诧級
     * @return 瀛樺偍閫傞厤鍣ㄥ疄渚?
     * @throws Exception
     */
    IStorageAdapter createAdapter(String storageType, String storageConfig) throws Exception;
    
    /**
     * 鑾峰彇瀛樺偍閫傞厤鍣紙濡傛灉宸插瓨鍦ㄥ垯杩斿洖锛屽惁鍒欏垱寤猴級
     * 
     * @param storageType 瀛樺偍绫诲瀷
     * @param config 瀛樺偍閰嶇疆
     * @return 瀛樺偍閫傞厤鍣ㄥ疄渚?
     * @throws Exception
     */
    IStorageAdapter getAdapter(StorageType storageType, StorageConfig config) throws Exception;
    
    /**
     * 鑾峰彇瀛樺偍閫傞厤鍣紙浣跨敤瀛楃涓茬被鍨嬶紝淇濇寔鍚戝悗鍏煎锛?
     * 
     * @param storageType 瀛樺偍绫诲瀷
     * @param storageConfig 瀛樺偍閰嶇疆锛圝SON鏍煎紡瀛楃涓诧級
     * @return 瀛樺偍閫傞厤鍣ㄥ疄渚?
     * @throws Exception
     */
    IStorageAdapter getAdapter(String storageType, String storageConfig) throws Exception;
    
    /**
     * 鑾峰彇鏀寔鐨勫瓨鍌ㄧ被鍨嬪垪琛?
     * 
     * @return 鏀寔鐨勫瓨鍌ㄧ被鍨嬪垪琛?
     */
    List<StorageType> getSupportedTypes();
    
    /**
     * 鍒ゆ柇鏄惁鏀寔鎸囧畾鐨勫瓨鍌ㄧ被鍨?
     * 
     * @param storageType 瀛樺偍绫诲瀷
     * @return 鏄惁鏀寔
     */
    boolean supports(StorageType storageType);
    
    /**
     * 娉ㄥ唽瀛樺偍閫傞厤鍣ㄥ疄鐜?
     * 
     * @param storageType 瀛樺偍绫诲瀷
     * @param adapterClass 閫傞厤鍣ㄥ疄鐜扮被
     */
    void registerAdapter(StorageType storageType, Class<? extends IStorageAdapter> adapterClass);
}

