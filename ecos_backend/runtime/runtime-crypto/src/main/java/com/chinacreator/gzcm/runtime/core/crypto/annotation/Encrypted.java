package com.chinacreator.gzcm.runtime.core.crypto.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 鍔犲瘑瀛楁娉ㄨВ
 * 鏍囪闇€瑕佸姞瀵嗙殑瀛楁锛屾鏋朵細鑷姩澶勭悊鍔犲瘑/瑙ｅ瘑
 * 
 * @author CDRC Runtime Team
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Encrypted {
    
    /**
     * 瀵嗛挜ID
     * 濡傛灉涓虹┖锛屼娇鐢ㄩ粯璁ゅ瘑閽?
     */
    String keyId() default "";
    
    /**
     * 鍔犲瘑绠楁硶
     * 榛樿浣跨敤AES-256-GCM
     */
    String algorithm() default "AES-256-GCM";
    
    /**
     * 鏄惁鍦ㄥ瓨鍌ㄦ椂鍔犲瘑
     * 榛樿true
     */
    boolean encryptOnStore() default true;
    
    /**
     * 鏄惁鍦ㄨ鍙栨椂瑙ｅ瘑
     * 榛樿true
     */
    boolean decryptOnLoad() default true;
}

