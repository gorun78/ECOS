package com.chinacreator.gzcm.runtime.core.legacy.util;

/**
 * 应用数据库名称线程本地变量
 * 用于在多租户环境中存储当前线程的数据库名称
 */
public class AppDBNameThreadLocal {
    
    private static final ThreadLocal<String> dbNameHolder = new ThreadLocal<>();
    
    /**
     * 设置当前线程的数据库名称
     * @param dbName 数据库名称
     */
    public static void set(String dbName) {
        dbNameHolder.set(dbName);
    }
    
    /**
     * 获取当前线程的数据库名称
     * @return 数据库名称
     */
    public static String get() {
        return dbNameHolder.get();
    }
    
    /**
     * 添加数据库名称（用于多数据库场景）
     * @param dbName 数据库名称
     */
    public static void add(String dbName) {
        set(dbName);
    }
    
    /**
     * 清除当前线程的数据库名称
     */
    public static void remove() {
        dbNameHolder.remove();
    }
}
