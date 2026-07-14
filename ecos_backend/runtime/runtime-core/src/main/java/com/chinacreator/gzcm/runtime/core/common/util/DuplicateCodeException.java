package com.chinacreator.gzcm.runtime.core.common.util;

/**
 * 重复编码异常
 */
public class DuplicateCodeException extends Exception {
    private static final long serialVersionUID = 1L;
    
    private OperationType operationType;
    
    public DuplicateCodeException(OperationType operationType) {
        super("编码重复");
        this.operationType = operationType;
    }
    
    public DuplicateCodeException(String message, OperationType operationType) {
        super(message);
        this.operationType = operationType;
    }
    
    public OperationType getOperationType() {
        return operationType;
    }
}
