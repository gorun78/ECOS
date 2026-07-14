package com.chinacreator.gzcm.runtime.core.format.compression;

/**
 * 鍘嬬缉寮傚父
 * 
 * @author CDRC Runtime Team
 */
public class CompressionException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    private String algorithm;
    private String operation;
    
    public CompressionException(String message) {
        super(message);
    }
    
    public CompressionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public CompressionException(String algorithm, String operation, String message) {
        super(String.format("鍘嬬缉绠楁硶[%s]鐨?s鎿嶄綔澶辫触: %s", algorithm, operation, message));
        this.algorithm = algorithm;
        this.operation = operation;
    }
    
    public CompressionException(String algorithm, String operation, String message, Throwable cause) {
        super(String.format("鍘嬬缉绠楁硶[%s]鐨?s鎿嶄綔澶辫触: %s", algorithm, operation, message), cause);
        this.algorithm = algorithm;
        this.operation = operation;
    }
    
    public String getAlgorithm() {
        return algorithm;
    }
    
    public String getOperation() {
        return operation;
    }
}

