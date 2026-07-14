package com.chinacreator.gzcm.runtime.core.crypto.annotation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.chinacreator.gzcm.runtime.core.crypto.IKeyManagementService;
import com.chinacreator.gzcm.runtime.core.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.runtime.core.crypto.impl.DataEncryptionServiceImpl;
import com.chinacreator.gzcm.runtime.core.crypto.service.impl.KeyManagementServiceImpl;

/**
 * 加密字段处理器
 * 使用反射处理@Encrypted注解的字段
 * 
 * @author CDRC Runtime Team
 */
public class EncryptedFieldProcessor {
    
    private final IKeyManagementService keyManagementService;
    private final IDataEncryptionService encryptionService;
    
    public EncryptedFieldProcessor() {
        this.keyManagementService = new KeyManagementServiceImpl();
        this.encryptionService = new DataEncryptionServiceImpl(keyManagementService);
    }
    
    public EncryptedFieldProcessor(IKeyManagementService keyManagementService, 
                                   IDataEncryptionService encryptionService) {
        this.keyManagementService = keyManagementService;
        this.encryptionService = encryptionService;
    }
    
    /**
     * 加密对象中标记了@Encrypted的字段
     * 
     * @param obj 待加密的对象
     * @throws Exception
     */
    public void encryptFields(Object obj) throws Exception {
        if (obj == null) {
            return;
        }
        
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            Encrypted annotation = field.getAnnotation(Encrypted.class);
            if (annotation != null && annotation.encryptOnStore()) {
                field.setAccessible(true);
                Object value = field.get(obj);
                
                if (value != null && value instanceof String) {
                    String encrypted = encryptValue((String) value, annotation);
                    field.set(obj, encrypted);
                }
            }
        }
    }
    
    /**
     * 解密对象中标记了@Encrypted的字段
     * 
     * @param obj 待解密的对象
     * @throws Exception
     */
    public void decryptFields(Object obj) throws Exception {
        if (obj == null) {
            return;
        }
        
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            Encrypted annotation = field.getAnnotation(Encrypted.class);
            if (annotation != null && annotation.decryptOnLoad()) {
                field.setAccessible(true);
                Object value = field.get(obj);
                
                if (value != null && value instanceof String) {
                    String decrypted = decryptValue((String) value, annotation);
                    field.set(obj, decrypted);
                }
            }
        }
    }
    
    /**
     * 加密值
     */
    private String encryptValue(String value, Encrypted annotation) throws Exception {
        String keyId = annotation.keyId();
        if (keyId == null || keyId.isEmpty()) {
            keyId = "default-key";
        }
        
        // 确保密钥存在
        if (keyManagementService.getKey(keyId) == null) {
            keyManagementService.createKey(keyId, annotation.algorithm().replace("-256-GCM", ""), 256);
        }
        
        byte[] encrypted = encryptionService.encrypt(value.getBytes("UTF-8"), keyId);
        return java.util.Base64.getEncoder().encodeToString(encrypted);
    }
    
    /**
     * 解密值
     */
    private String decryptValue(String encryptedValue, Encrypted annotation) throws Exception {
        String keyId = annotation.keyId();
        if (keyId == null || keyId.isEmpty()) {
            keyId = "default-key";
        }
        
        byte[] encrypted = java.util.Base64.getDecoder().decode(encryptedValue);
        byte[] decrypted = encryptionService.decrypt(encrypted, keyId);
        return new String(decrypted, "UTF-8");
    }
    
    /**
     * 获取对象中所有标记了@Encrypted的字段
     */
    public List<Field> getEncryptedFields(Class<?> clazz) {
        List<Field> encryptedFields = new ArrayList<>();
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            if (field.getAnnotation(Encrypted.class) != null) {
                encryptedFields.add(field);
            }
        }
        
        return encryptedFields;
    }
}

