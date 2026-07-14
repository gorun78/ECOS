package com.chinacreator.gzcm.runtime.core.common.util;

/**
 * 重复简要名称异常
 */
public class DuplicateBriefNameException extends Exception {
    private static final long serialVersionUID = 1L;
    
    private OperationType operationType;
    
    public DuplicateBriefNameException(OperationType operationType) {
        super("简要名称重复");
        this.operationType = operationType;
    }
    
    public DuplicateBriefNameException(String message, OperationType operationType) {
        super(message);
        this.operationType = operationType;
    }
    
    public OperationType getOperationType() {
        return operationType;
    }
}
