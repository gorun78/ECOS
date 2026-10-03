package com.chinacreator.gzcm.engine.kb.shared;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

/**
 * 知识域写入主体（actor）与角色解析工具（F04-06/07 写路径可归因，F04-16 前简化口径）。
 * <p>
 * 取 actor：读 {@code SecurityContextHolder} 的 JWT 主体（principal / name）；取不到时回退 {@code "system"}
 * ——<b>禁止</b>使用 {@code 'current-user'} 字面量（K-42 纠正，铁律 F04-16 可归因写入）。
 * <p>
 * 知识管理员判定（发布画像 / 审批假设）：当前未接入 security-engine ABAC Bean，
 * <b>简化判定</b>为"请求上下文角色含 {@code knowledge-admin}"；F04-16 落地后经
 * security-engine decide 收紧（详见 {@code KbProfileService.publish} 注释）。
 */
public final class KbActorSupport {

    private KbActorSupport() {
    }

    /** 知识管理员角色码（F04-16 收紧前的简化判定口径）。 */
    public static final String ROLE_KNOWLEDGE_ADMIN = "knowledge-admin";

    /**
     * 解析当前请求主体（JWT principal）。取不到（匿名/未认证）时返回 {@code "system"}。
     * <p>禁止返回 {@code 'current-user'} 字面量。
     */
    public static String currentActor() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !isAnonymous(auth)) {
                Object principal = auth.getPrincipal();
                if (principal instanceof String s && !s.isBlank()) {
                    return s;
                }
                if (auth.getName() != null && !auth.getName().isBlank() && !"anonymousUser".equals(auth.getName())) {
                    return auth.getName();
                }
            }
        } catch (Exception ignored) {
            // 解析失败降级 system，不抛
        }
        return "system";
    }

    private static boolean isAnonymous(Authentication auth) {
        return (auth.getAuthorities() != null
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ANONYMOUS".equals(a.getAuthority())));
    }

    /**
     * 判断当前请求主体是否具备知识管理员角色。
     * <p>简化口径：读 SecurityContext 授权集合中含 {@link #ROLE_KNOWLEDGE_ADMIN} 即通过。
     * F04-16 落地后应改为经 security-engine {@code decide(principal, resource, WRITE)} 裁决（默认 DENY）。
     *
     * @return true 表示是知识管理员
     */
    public static boolean isKnowledgeAdmin() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getAuthorities() != null) {
                for (GrantedAuthority ga : auth.getAuthorities()) {
                    String a = ga.getAuthority();
                    if (a == null) {
                        continue;
                    }
                    if (ROLE_KNOWLEDGE_ADMIN.equals(a) || ("ROLE_" + ROLE_KNOWLEDGE_ADMIN).equals(a)) {
                        return true;
                    }
                }
                // 兼容：主体名直接携带角色（部分测试/JWT 实现将 roles 塞进 subject）
                String principalStr = auth.getPrincipal() instanceof String s ? s : auth.getName();
                if (principalStr != null && principalStr.contains(ROLE_KNOWLEDGE_ADMIN)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return false;
    }

    /**
     * 判断主体角色集合是否含 {@code knowledge-admin}（供单元测试直接注入角色，绕过 SecurityContext 静态态）。
     *
     * @param roles 主体的角色集合
     */
    public static boolean hasKnowledgeAdminRole(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.contains(ROLE_KNOWLEDGE_ADMIN) || roles.contains("ROLE_" + ROLE_KNOWLEDGE_ADMIN);
    }

    /** 应用侧生成主键 UUID（MC01：VARCHAR(36) 应用侧生成，禁 gen_random_uuid()）。 */
    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
