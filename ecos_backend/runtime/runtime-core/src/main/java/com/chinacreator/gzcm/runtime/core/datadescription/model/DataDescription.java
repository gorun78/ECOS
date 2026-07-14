package com.chinacreator.gzcm.runtime.core.datadescription.model;

import com.chinacreator.gzcm.runtime.core.datadescription.enums.DataType;

/**
 * 鏁版嵁鎻忚堪鎺ュ彛
 * 缁熶竴鐨勬暟鎹弿杩版娊璞★紝鏀寔澶氱鏁版嵁绫诲瀷
 * 
 * @author CDRC Runtime Team
 */
public interface DataDescription {
    
    /**
     * 鑾峰彇鏁版嵁绫诲瀷
     * 
     * @return 鏁版嵁绫诲瀷鏋氫妇
     */
    DataType getDataType();
    
    /**
     * 鑾峰彇Schema
     * 
     * @return 鏁版嵁Schema锛屽彲鑳戒负null
     */
    DataSchema getSchema();
    
    /**
     * 璁剧疆Schema
     * 
     * @param schema 鏁版嵁Schema
     */
    void setSchema(DataSchema schema);
    
    /**
     * 鑾峰彇鍏冩暟鎹?
     * 
     * @return 鏁版嵁鍏冩暟鎹?
     */
    DataMetadata getMetadata();
    
    /**
     * 璁剧疆鍏冩暟鎹?
     * 
     * @param metadata 鏁版嵁鍏冩暟鎹?
     */
    void setMetadata(DataMetadata metadata);
    
    /**
     * 楠岃瘉鏁版嵁鎻忚堪
     * 妫€鏌ユ暟鎹弿杩版槸鍚﹀畬鏁村拰鏈夋晥
     * 
     * @return true琛ㄧず鏁版嵁鎻忚堪鏈夋晥
     * @throws ValidationException 楠岃瘉澶辫触鏃舵姏鍑哄紓甯?
     */
    boolean validate() throws ValidationException;
    
    /**
     * 楠岃瘉鏁版嵁鏄惁绗﹀悎鎻忚堪
     * 
     * @param data 寰呴獙璇佺殑鏁版嵁
     * @return true琛ㄧず鏁版嵁绗﹀悎鎻忚堪
     * @throws ValidationException 楠岃瘉澶辫触鏃舵姏鍑哄紓甯?
     */
    boolean validateData(Object data) throws ValidationException;
    
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

