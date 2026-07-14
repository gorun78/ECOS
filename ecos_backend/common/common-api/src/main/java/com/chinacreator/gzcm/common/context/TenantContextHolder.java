package com.chinacreator.gzcm.common.context;

/**
 * 多租户上下文持有者 — 基于 ThreadLocal 存储当前请求的租户ID。
 *
 * <p>在 QuotaFilter 中设置，在业务逻辑中通过 {@link #getTenantId()} 读取，
 * 请求结束后必须调用 {@link #clear()} 清理，防止内存泄漏。
 */
public final class TenantContextHolder {

    private static final ThreadLocal<String> TENANT_ID_HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {
        // 工具类，禁止实例化
    }

    /** 设置当前租户ID */
    public static void setTenantId(String tenantId) {
        TENANT_ID_HOLDER.set(tenantId);
    }

    /** 获取当前租户ID，未设置时返回 null */
    public static String getTenantId() {
        return TENANT_ID_HOLDER.get();
    }

    /** 清理 ThreadLocal，必须在请求结束后调用 */
    public static void clear() {
        TENANT_ID_HOLDER.remove();
    }
}
