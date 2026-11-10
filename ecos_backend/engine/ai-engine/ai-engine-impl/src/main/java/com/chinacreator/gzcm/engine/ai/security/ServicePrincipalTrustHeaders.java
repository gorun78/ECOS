package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;
import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 出站信任头还原（Copilot 工具腿 + 护栏腿）。
 *
 * <p><b>缺口形态（活体实证，不是推断）</b>：gateway {@code EcosTrustHeaderFilter} 在链首
 * 无条件剥离 {@code X-ECOS-USER/ROLE/TENANT}，入口请求已不带信任头；出站腿只中继
 * {@code Authorization}（{@link CallerCredentialSource}）时，属主 {@code HeaderAuthInterceptor}
 * 读不到 user/tenant ⇒ 403 {@code ECOS-AUTH-010}。护栏腿把这个 403 折成
 * {@code GUARDRAIL_FAIL_CLOSED}(503「安全服务不可用」)，与真实的策略拒绝混为一谈
 * （§4.7.2「不可用 ≠ 无权限」）。修法与 gateway {@code WorkspaceForwardController} 转发腿一致：
 * 从<b>已验签的</b>服务端主体还原 user/role/tenant。</p>
 *
 * <p><b>纪律</b>：① 入口已带信任头时逐字优先，本类不覆盖；② 主体只取
 * {@link SecurityContextHolder}（JWT 验签产物）与 {@link TenantContextHolder}，
 * 不读请求体/入参自报值；③ 取不到即不写头，由属主 fail-closed，禁兜底主语。</p>
 */
public final class ServicePrincipalTrustHeaders {

    private ServicePrincipalTrustHeaders() {
    }

    /** 中继已有凭证，并补齐被网关剥离的信任头（缺失即不写）。 */
    public static void apply(HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        CallerCredentialSource.applyTo(headers);
        Authentication auth = currentAuthentication();
        if (!headers.containsKey(HeaderAuthInterceptor.H_USER) && auth != null) {
            String user = principalName(auth);
            if (user != null && !user.isBlank()) {
                headers.set(HeaderAuthInterceptor.H_USER, user);
            }
        }
        if (!headers.containsKey(HeaderAuthInterceptor.H_ROLE) && auth != null) {
            String roles = roleList(auth);
            if (!roles.isBlank()) {
                headers.set(HeaderAuthInterceptor.H_ROLE, roles);
            }
        }
        if (!headers.containsKey(HeaderAuthInterceptor.H_TENANT)) {
            String tenant = TenantContextHolder.getTenantId();
            if (tenant != null && !tenant.isBlank()) {
                headers.set(HeaderAuthInterceptor.H_TENANT, tenant);
            }
        }
    }

    private static Authentication currentAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth;
    }

    private static String principalName(Authentication auth) {
        Object principal = auth.getPrincipal();
        String name = principal instanceof String s ? s : auth.getName();
        return name == null || name.isBlank() || "anonymousUser".equals(name) ? null : name;
    }

    private static String roleList(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .filter(a -> !a.isBlank())
                .collect(Collectors.joining(","));
    }
}
