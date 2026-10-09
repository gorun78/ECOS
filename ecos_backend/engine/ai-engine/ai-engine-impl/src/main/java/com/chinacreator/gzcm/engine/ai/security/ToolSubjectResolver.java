package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 裁决主体解析（详细设计-06 F06-02 要点 1：{@code subject} 只能由服务端 token 上下文构造）。
 *
 * <p>解析顺序（与既有 R1.3/H8-T4 口径一致，禁取工具入参/请求体自报值）：</p>
 * <ol>
 *   <li>sysman {@code UserContext}（ThreadLocal，由 HeaderAuthInterceptor 从 gateway token 链路还原）
 *       —— 取 userId / tenantId / claims 内的 roles、department；</li>
 *   <li>Spring {@code SecurityContextHolder} —— userId 兜底 + authorities 转 roles
 *       （独立 ai-engine-boot 进程无 sysman 类时的同语义降级，不放宽主体来源）。</li>
 * </ol>
 *
 * <p>无主体时返回 {@code userId=null} 的 Subject，由裁决闸判 FAIL_CLOSED（§2.4-6）。</p>
 */
@Component
public class ToolSubjectResolver {

    private static final Logger log = LoggerFactory.getLogger(ToolSubjectResolver.class);

    private static final String USER_CONTEXT_FQN = "com.chinacreator.gzcm.sysman.iam.context.UserContext";

    public ToolAdjudicationRequest.Subject resolve() {
        Object userContext = currentUserContext();
        String userId = readContextString(userContext, "getUserId");
        String tenantId = readContextString(userContext, "getTenantId");
        Set<String> roles = new LinkedHashSet<>();
        String department = null;

        Object claims = invoke(userContext, "getClaims");
        if (claims instanceof Map<?, ?> map) {
            addAll(roles, map.get("roles"));
            addAll(roles, map.get("role"));
            Object dept = map.get("department");
            if (dept != null) department = String.valueOf(dept);
            if (userId == null) {
                Object uid = map.get("userId");
                if (uid != null) userId = String.valueOf(uid);
            }
            if (tenantId == null) {
                Object tid = map.get("tenantId");
                if (tid != null) tenantId = String.valueOf(tid);
            }
        }
        if (tenantId == null) {
            tenantId = TenantContextHolder.getTenantId();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            for (GrantedAuthority ga : auth.getAuthorities()) {
                if (ga != null && ga.getAuthority() != null && !ga.getAuthority().isBlank()) {
                    roles.add(ga.getAuthority());
                }
            }
            if (userId == null || userId.isBlank()) {
                userId = principalName(auth.getPrincipal());
            }
        }
        return new ToolAdjudicationRequest.Subject(userId, new ArrayList<>(roles), department, tenantId);
    }

    private Object currentUserContext() {
        try {
            Class<?> uc = Class.forName(USER_CONTEXT_FQN);
            return uc.getMethod("getCurrent").invoke(null);
        } catch (Throwable ignored) {
            // sysman 不在 classpath（独立 ai-engine-boot 进程）→ 落到 SecurityContext
            return null;
        }
    }

    private String readContextString(Object target, String getter) {
        Object v = invoke(target, getter);
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private Object invoke(Object target, String getter) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(getter);
            return m.invoke(target);
        } catch (Exception e) {
            log.debug("ToolSubjectResolver: 读取 {} 失败: {}", getter, e.getMessage());
            return null;
        }
    }

    private String principalName(Object principal) {
        if (principal == null) return null;
        if (principal instanceof String s) return s;
        String s = principal.toString();
        return "anonymousUser".equals(s) ? null : s;
    }

    private void addAll(Set<String> target, Object value) {
        if (value == null) return;
        if (value instanceof java.util.Collection<?> c) {
            for (Object o : c) {
                if (o != null && !String.valueOf(o).isBlank()) target.add(String.valueOf(o));
            }
        } else {
            String s = String.valueOf(value);
            for (String part : s.split(",")) {
                if (!part.isBlank()) target.add(part.trim());
            }
        }
    }
}
