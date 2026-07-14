package com.chinacreator.gzcm.runtime.core.datadescription.model;

/**
 * 鏁版嵁Schema鎺ュ彛
 * 瀹氫箟鏁版嵁鐨勭粨鏋勫拰楠岃瘉瑙勫垯
 * 
 * @author CDRC Runtime Team
 */
public interface DataSchema {
    
    /**
     * Schema绫诲瀷鏋氫妇
     */
    enum SchemaType {
        JSON_SCHEMA("JSON Schema"),
        AVRO("Avro Schema"),
        PROTOBUF("Protobuf Schema"),
        XML_SCHEMA("XML Schema"),
        CUSTOM("鑷畾涔塖chema");
        
        private final String displayName;
        
        SchemaType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * 鑾峰彇Schema绫诲瀷
     * 
     * @return Schema绫诲瀷
     */
    SchemaType getSchemaType();
    
    /**
     * 鑾峰彇Schema鍐呭
     * 
     * @return Schema鍐呭锛圝SON瀛楃涓层€丄vro瀹氫箟绛夛級
     */
    String getSchemaContent();
    
    /**
     * 楠岃瘉鏁版嵁鏄惁绗﹀悎Schema
     * 
     * @param data 寰呴獙璇佺殑鏁版嵁
     * @return true琛ㄧず鏁版嵁绗﹀悎Schema锛宖alse琛ㄧず涓嶇鍚?
     * @throws ValidationException 楠岃瘉杩囩▼涓彂鐢熷紓甯?
     */
    boolean validate(Object data) throws ValidationException;
    
    /**
     * 鑾峰彇Schema鐗堟湰
     * 
     * @return Schema鐗堟湰鍙?
     */
    String getVersion();
    
    /**
     * 璁剧疆Schema鐗堟湰
     * 
     * @param version 鐗堟湰鍙?
     */
    void setVersion(String version);
    
    /**
     * 楠岃瘉寮傚父绫?
     */
    class ValidationException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public ValidationException(String message) {
            super(message);
        }
        
        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

