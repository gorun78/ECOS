package com.chinacreator.gzcm.common.security.header;

/**
 * 跨 JVM 身份还原后的请求上下文（限服务内消费，非跨线程泄漏）。
 *
 * <p>由 {@link HeaderAuthInterceptor} 在 doPreHandle 时以 X-ECOS-USER/ROLE/TENANT/DOMAIN 与 X-Request-Id 头构造，
 * 在 afterCompletion 时必须显式 {@link #clear()}，防线程池串号——
 * 对齐架构铁律 §2.4（无上下文即无身份）与 C.2.2 目标态。
 *
 * <p>本类<b>不</b>负责安全策略裁决（RLS/CLS/OPA 归 01 册），仅承载
 * identity / tenant / trust 四元组，供后续 AOP 与网关诊断页读取。
 *
 * <p>_threadLocal 命名故意带 {@code ServiceContext} 前缀，与
 * {@code sysman.iam.context.UserContext} 区分，避免 gateway 双写时互相覆盖。</p>
 */
public final class ServiceContext {

    /** 请求级 traceId（X-Request-Id 入口透传）——无则由过滤器派生。 */
    private final String traceId;
    /** 跨 JVM 身份：JWT sub。 */
    private final String userId;
    /** 跨 JVM 角色：逗号分隔角色清单（JWT roles claim）。空串视为空集。 */
    private final String roles;
    /** 跨 JVM 租户：JWT tenant_id 或显式回查值。缺失时不应构造本上下文（拦截器 403）。 */
    private final String tenantId;
    /** 跨 JVM 业务域：control/business + 具体域，可选。 */
    private final String domain;
    /** 调用方服务标识（X-ECOS-SERVICE 的 serviceId 段，HMAC 验证通过后写入）。 */
    private final String serviceId;

    private static final ThreadLocal<ServiceContext> HOLDER = new ThreadLocal<>();

    private ServiceContext(String traceId, String userId, String roles,
                           String tenantId, String domain, String serviceId) {
        this.traceId = traceId;
        this.userId = userId;
        this.roles = roles;
        this.tenantId = tenantId;
        this.domain = domain;
        this.serviceId = serviceId;
    }

    public static ServiceContext of(String traceId, String userId, String roles,
                                    String tenantId, String domain, String serviceId) {
        return new ServiceContext(traceId, userId, roles, tenantId, domain, serviceId);
    }

    public static void set(ServiceContext ctx) {
        HOLDER.set(ctx);
    }

    public static ServiceContext current() {
        return HOLDER.get();
    }

    /**
     * 线程池复用防护：MVC afterCompletion 必须调用，防止线程被下一请求复用后读到上一请求的身份。
     */
    public static void clear() {
        HOLDER.remove();
    }

    public static boolean present() {
        return HOLDER.get() != null;
    }

    public String getTraceId() { return traceId; }
    public String getUserId() { return userId; }
    public String getRoles() { return roles; }
    public String getTenantId() { return tenantId; }
    public String getDomain() { return domain; }
    public String getServiceId() { return serviceId; }

    @Override
    public String toString() {
        return "ServiceContext{userId=" + userId + ", tenant=" + tenantId
                + ", domain=" + domain + ", serviceId=" + serviceId + "}";
    }
}
