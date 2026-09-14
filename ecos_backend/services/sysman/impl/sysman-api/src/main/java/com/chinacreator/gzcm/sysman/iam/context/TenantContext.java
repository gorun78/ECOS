package com.chinacreator.gzcm.sysman.iam.context;

/**
 * 租户上下文（基于ThreadLocal）
 */
public class TenantContext {

    private static final ThreadLocal<String> TENANT_HOLDER = new ThreadLocal<>();

    public static void setTenantId(String tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    public static String getTenantId() {
        return TENANT_HOLDER.get();
    }

    public static void clear() {
        TENANT_HOLDER.remove();
    }
}


