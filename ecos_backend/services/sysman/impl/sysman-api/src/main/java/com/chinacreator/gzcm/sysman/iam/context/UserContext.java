package com.chinacreator.gzcm.sysman.iam.context;

import java.util.HashMap;
import java.util.Map;

public class UserContext {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private String userId;
    private String username;
    private String tenantId;
    private String sessionId;
    private String traceId;
    private String clientIp;
    private Map<String, Object> claims;

    public static UserContext getCurrent() {
        return CONTEXT.get();
    }

    public static void setCurrent(UserContext context) {
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static String getCurrentUserId() {
        UserContext ctx = getCurrent();
        return ctx != null ? ctx.getUserId() : null;
    }

    public static String getCurrentUsername() {
        UserContext ctx = getCurrent();
        return ctx != null ? ctx.getUsername() : null;
    }

    public static String getCurrentTenantId() {
        UserContext ctx = getCurrent();
        return ctx != null ? ctx.getTenantId() : null;
    }

    public static UserContext fromTokenClaims(Map<String, Object> claims) {
        UserContext context = new UserContext();
        context.setUserId((String) claims.get("userId"));
        context.setUsername((String) claims.get("username"));
        context.setTenantId((String) claims.get("tenantId"));
        context.setSessionId((String) claims.get("sessionId"));
        context.setClaims(claims);
        return context;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        TenantContext.setTenantId(tenantId);
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    public void setClaims(Map<String, Object> claims) {
        this.claims = claims != null ? claims : new HashMap<>();
    }

    public Object getClaim(String key) {
        return claims != null ? claims.get(key) : null;
    }

    @Override
    public String toString() {
        return "UserContext{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}
