package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 出站调用凭证中继（H10 服务凭证收口前的 ADR-7 信任链唯一合法通道，卷07 F07-17 口径）。
 *
 * <p><b>为什么需要它</b>：workspace 独立进程（:18090）向引擎侧发 REST 时若不携带任何凭证，
 * 引擎宿主（gateway/aiming/sysman）的 Spring Security 默认 DENY ⇒ 已登录用户被判 403，
 * 调用方把"plumbing 缺口"误读成"权限拒绝"（2026-10-11 live 实证，见
 * {@code WorkspaceSecurityEngineClient#relayCallerCredentials} 的整改记录）。</p>
 *
 * <p><b>fail-closed 语义保持不变</b>：无入口请求上下文（异步任务/定时线程）⇒ <b>不伪造凭证</b>，
 * 由被调方按其默认策略裁决；只中继入口请求已带的 {@code Authorization} 与
 * {@code X-ECOS-USER/ROLE/TENANT/DOMAIN} 信任头，逐字转发、不升权、不拼接。</p>
 *
 * <p>本类是把 {@code WorkspaceSecurityEngineClient} 内的同义私有方法抽为可复用件，
 * 供新增的认知域 run 适配器使用；security 客户端自身的收敛属后续批次（不在本批夹带）。</p>
 */
public final class CallerCredentialRelay {

    private CallerCredentialRelay() {}

    /** 把当前入口请求的凭证与信任头中继到出站请求头（无请求上下文时原样不加）。 */
    public static void applyTo(HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return;
        }
        HttpServletRequest request = servletAttrs.getRequest();
        String authorization = trimToNull(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        copyIfPresent(request, headers, HeaderAuthInterceptor.H_USER);
        copyIfPresent(request, headers, HeaderAuthInterceptor.H_ROLE);
        copyIfPresent(request, headers, HeaderAuthInterceptor.H_TENANT);
        copyIfPresent(request, headers, HeaderAuthInterceptor.H_DOMAIN);
    }

    private static void copyIfPresent(HttpServletRequest request, HttpHeaders headers, String header) {
        String value = trimToNull(request.getHeader(header));
        if (value != null) {
            headers.set(header, value);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
