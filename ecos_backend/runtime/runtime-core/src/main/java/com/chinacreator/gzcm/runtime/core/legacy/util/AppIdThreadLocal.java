package com.chinacreator.gzcm.runtime.core.legacy.util;

/**
 * 应用ID线程本地变量
 * 用于在多租户环境中存储当前线程的应用ID
 */
public class AppIdThreadLocal {

    private static final ThreadLocal<String> appIdHolder = new ThreadLocal<>();

    /**
     * 设置当前线程的应用ID
     * @param appId 应用ID
     */
    public static void set(String appId) {
        appIdHolder.set(appId);
    }

    /**
     * 获取当前线程的应用ID
     * @return 应用ID
     */
    public static String get() {
        return appIdHolder.get();
    }

    /**
     * 添加应用ID（用于多应用场景）
     * @param appId 应用ID
     */
    public static void add(String appId) {
        set(appId);
    }

    /**
     * 清除当前线程的应用ID
     */
    public static void remove() {
        appIdHolder.remove();
    }
}