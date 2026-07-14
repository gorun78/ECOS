package com.chinacreator.gzcm.runtime.core.transform;

/**
 * 转换异常
 */
public class TransformException extends Exception {
    
    private static final long serialVersionUID = 1L;

    public TransformException() {
        super();
    }

    public TransformException(String message) {
        super(message);
    }

    public TransformException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransformException(Throwable cause) {
        super(cause);
    }
}
