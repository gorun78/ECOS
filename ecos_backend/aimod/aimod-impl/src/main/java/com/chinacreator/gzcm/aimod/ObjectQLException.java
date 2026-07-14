package com.chinacreator.gzcm.aimod;

/**
 * ObjectQL 解析异常。
 */
public class ObjectQLException extends RuntimeException {

    public ObjectQLException(String message) {
        super(message);
    }

    public ObjectQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
