package com.chinacreator.gzcm.runtime.core.kettle;

/**
 * Kettle瀵倸鐖?
 * 
 * @author CDRC Runtime Team
 */
public class KettleException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    private String operation;
    
    public KettleException(String message) {
        super(message);
    }
    
    public KettleException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public KettleException(String operation, String message) {
        super(String.format("Kettle閹垮秳缍擺%s]婢惰精瑙? %s", operation, message));
        this.operation = operation;
    }
    
    public KettleException(String operation, String message, Throwable cause) {
        super(String.format("Kettle閹垮秳缍擺%s]婢惰精瑙? %s", operation, message), cause);
        this.operation = operation;
    }
    
    public String getOperation() {
        return operation;
    }
}

