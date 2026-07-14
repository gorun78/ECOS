package com.chinacreator.gzcm.runtime.core.format;

/**
 * 鏍煎紡寮傚父
 * 
 * @author CDRC Runtime Team
 */
public class FormatException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    private String format;
    private String operation;
    
    public FormatException(String message) {
        super(message);
    }
    
    public FormatException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public FormatException(String format, String operation, String message) {
        super(String.format("鏍煎紡[%s]鐨?s鎿嶄綔澶辫触: %s", format, operation, message));
        this.format = format;
        this.operation = operation;
    }
    
    public FormatException(String format, String operation, String message, Throwable cause) {
        super(String.format("鏍煎紡[%s]鐨?s鎿嶄綔澶辫触: %s", format, operation, message), cause);
        this.format = format;
        this.operation = operation;
    }
    
    public String getFormat() {
        return format;
    }
    
    public String getOperation() {
        return operation;
    }
}

