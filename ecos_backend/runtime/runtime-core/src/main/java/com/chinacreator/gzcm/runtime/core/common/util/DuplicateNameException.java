package com.chinacreator.gzcm.runtime.core.common.util;

/**
 * 重复名称异常
 */
public class DuplicateNameException extends Exception {
    private static final long serialVersionUID = 1L;
    
    private OperationType operationType;
    
    public DuplicateNameException(OperationType operationType) {
        super("名称重复");
        this.operationType = operationType;
    }
    
    public DuplicateNameException(String message, OperationType operationType) {
        super(message);
        this.operationType = operationType;
    }
    
    public OperationType getOperationType() {
        return operationType;
    }
}
