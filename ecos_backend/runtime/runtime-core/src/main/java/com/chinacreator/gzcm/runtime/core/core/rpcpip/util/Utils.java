package com.chinacreator.gzcm.runtime.core.core.rpcpip.util;

/**
 * RPC工具类
 * 用于RPC相关的工具方法
 */
public class Utils {
    
    /**
     * 生成唯一ID
     * @return 唯一ID
     */
    public static String generateId() {
        return java.util.UUID.randomUUID().toString();
    }
    
    /**
     * 格式化消息
     * @param message 消息模板
     * @param args 参数
     * @return 格式化后的消息
     */
    public static String formatMessage(String message, Object... args) {
        if (message == null) {
            return null;
        }
        if (args == null || args.length == 0) {
            return message;
        }
        return String.format(message, args);
    }
}
