package com.chinacreator.gzcm.runtime.core.crypto.util;

import java.util.ArrayList;
import java.util.List;

import com.chinacreator.gzcm.runtime.core.crypto.IKeyManagementService;
import com.chinacreator.gzcm.runtime.core.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.runtime.core.crypto.annotation.EncryptedFieldProcessor;
import com.chinacreator.gzcm.runtime.core.crypto.impl.DataEncryptionServiceImpl;
import com.chinacreator.gzcm.runtime.core.crypto.service.impl.KeyManagementServiceImpl;

/**
 * 加密工具类
 * 提供对象加密/解密的便捷方法
 * 
 * @author CDRC Runtime Team
 */
public class EncryptionUtils {
    
    private static final EncryptedFieldProcessor processor = new EncryptedFieldProcessor();
    
    /**
     * 加密对象中标记了@Encrypted的字段
     * 
     * @param obj 待加密的对象
     * @throws Exception
     */
    public static void encryptObject(Object obj) throws Exception {
        processor.encryptFields(obj);
    }
    
    /**
     * 解密对象中标记了@Encrypted的字段
     * 
     * @param obj 待解密的对象
     * @throws Exception
     */
    public static void decryptObject(Object obj) throws Exception {
        processor.decryptFields(obj);
    }
    
    /**
     * 批量加密对象
     * 
     * @param objects 待加密的对象列表
     * @throws Exception
     */
    public static void encryptObjects(List<?> objects) throws Exception {
        if (objects == null || objects.isEmpty()) {
            return;
        }
        
        for (Object obj : objects) {
            encryptObject(obj);
        }
    }
    
    /**
     * 批量解密对象
     * 
     * @param objects 待解密的对象列表
     * @throws Exception
     */
    public static void decryptObjects(List<?> objects) throws Exception {
        if (objects == null || objects.isEmpty()) {
            return;
        }
        
        for (Object obj : objects) {
            decryptObject(obj);
        }
    }
    
    /**
     * 创建自定义的加密字段处理器
     * 
     * @param keyManagementService 密钥管理服务
     * @param encryptionService 加密服务
     * @return 加密字段处理器
     */
    public static EncryptedFieldProcessor createProcessor(
            IKeyManagementService keyManagementService,
            IDataEncryptionService encryptionService) {
        return new EncryptedFieldProcessor(keyManagementService, encryptionService);
    }
}

