package com.chinacreator.gzcm.common.security.header;

/**
 * W02（详细设计-00 C.2.2）— 跨 JVM 身份上下文，HeaderAuthInterceptor 还原后写入。
 *
 * <p>ThreadLocal 生命周期与请求绑定：preHandle 写入，<b>afterCompletion 必须 clear</b>
 * （同 ClearanceInterceptor 的 ThreadLocal<HttpServletRequest> 口径，防线程池串号）。
 * 本类只承载上下文还原，**不做任何安全裁决**（裁决归 01 册 security-engine）。</p>
 */
public final class EcosServiceContext {

    private static final ThreadLocal<EcosServiceContext> CURRENT = new ThreadLocal<>();

    private String userId;
    private String tenantId;
    /** 逗号分隔角色（缺失视为空集） */
    private String roles;
    /** 业务域：control|business（+C.2.1 具体域） */
    private String domain;
    /** traceId（X-Request-Id 跨 JVM 还原，入 MDC 由拦截器负责） */
    private String traceId;

    private EcosServiceContext() {
    }

    private EcosServiceContext(String userId, String tenantId, String roles, String domain, String traceId) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.roles = roles;
        this.domain = domain;
        this.traceId = traceId;
    }

    public static EcosServiceContext of(String userId, String tenantId, String roles, String domain, String traceId) {
        return new EcosServiceContext(userId, tenantId, roles, domain, traceId);
    }

    public static void setCurrent(EcosServiceContext ctx) {
        CURRENT.set(ctx);
    }

    public static EcosServiceContext get() {
        return CURRENT.get();
    }

    public static EcosServiceContext currentOrEmpty() {
        return get() != null ? get() : new EcosServiceContext();
    }

    /** 请求结束必须调用（afterCompletion） */
    public static void clear() {
        CURRENT.remove();
    }

    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
    public String getRoles() { return roles; }
    public String getDomain() { return domain; }
    public String getTraceId() { return traceId; }
}
