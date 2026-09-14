package com.chinacreator.gzcm.sysman.common.exception;

/**
 * Sys-Man模块统一异常类
 * 支持国际化消息
 * 
 * @author CDRC Sys-Man Team
 */
public class SysManException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误码（用于错误消息键，如：error.2000）
     */
    private String errorCode;
    
    /**
     * 消息参数（用于国际化消息格式化）
     */
    private Object[] errorParams;
    
    /**
     * 消息键（用于国际化，如果设置了errorCode，则消息键为"error." + errorCode）
     */
    private String messageKey;
    
    public SysManException(String message) {
        super(message);
    }
    
    public SysManException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public SysManException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.messageKey = "error." + errorCode;
    }
    
    public SysManException(String errorCode, String message, Object... params) {
        super(message);
        this.errorCode = errorCode;
        this.messageKey = "error." + errorCode;
        this.errorParams = params;
    }
    
    public SysManException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.messageKey = "error." + errorCode;
    }
    
    /**
     * 使用消息键创建异常（支持国际化）
     * 
     * @param messageKey 消息键
     * @param defaultMessage 默认消息
     * @param cause 原因异常
     * @param params 消息参数
     */
    public SysManException(String messageKey, String defaultMessage, Throwable cause, Object... params) {
        super(defaultMessage, cause);
        this.messageKey = messageKey;
        this.errorParams = params;
        if (messageKey != null && messageKey.startsWith("error.")) {
            this.errorCode = messageKey.substring(6);
        }
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
        if (errorCode != null && messageKey == null) {
            this.messageKey = "error." + errorCode;
        }
    }
    
    public Object[] getErrorParams() {
        return errorParams;
    }
    
    public void setErrorParams(Object[] errorParams) {
        this.errorParams = errorParams;
    }
    
    public String getMessageKey() {
        return messageKey;
    }
    
    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
        if (messageKey != null && messageKey.startsWith("error.") && errorCode == null) {
            this.errorCode = messageKey.substring(6);
        }
    }
    
    /**
     * 检查是否包含国际化消息键
     */
    public boolean hasI18nMessage() {
        return messageKey != null && !messageKey.isEmpty();
    }
}

