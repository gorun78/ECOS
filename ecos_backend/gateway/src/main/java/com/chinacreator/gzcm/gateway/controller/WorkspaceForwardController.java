package com.chinacreator.gzcm.gateway.controller;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.security.http.ServerOrgAttribute;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 场景层（workspace :18090）承流转发（P0-1；2026-11-07 验证报告 A-1）。
 *
 * <p><b>为什么需要转发</b>：{@code GatewayApplication} 因同名 Bean 冲突整包排除
 * {@code workspace.controller.*} 与 {@code workspace.twin.*}，且 {@code VersionPrefixRewriteFilter}
 * 无 workspace 条目 ⇒ 前端 BFF 默认 gateway-first 时 {@code /api/v1/workspace/**} 直落 404
 * （实测 2026-11-07）。同 {@link GitForwardController} 先例，采用控制器转发而不是把
 * workspace 控制器扫进 gateway —— 后者会复活 Bean 名冲突。</p>
 *
 * <p><b>三条纪律</b>：① 客户端传入的 {@code X-ECOS-Server-Org} / {@code X-Org-Id} 一律丢弃
 * （只允许网关写内部头）；② 机构取 {@link ServerOrgAttribute}（服务端解析值），拿不到就不注入，
 * 由下游按 fail-closed 处置；③ workspace 不可达 ⇒ 503 + 机读码，绝不把转发失败折叠成
 * 200/404（「不可用 ≠ 不存在」）。</p>
 *
 * <p><b>三滤波器</b>：本类映射 {@code /api/v1/workspace/**}（正典，无需 rewrite；反证见
 * {@code WorkspaceRouteCarrierGateTest}），默认 {@code anyRequest().authenticated()}（不落 permitAll），
 * ClearanceInterceptor 按 {@code /api/v1/} 规则执行。</p>
 *
 * @author ECOS P0-1 修复批次
 */
@RestController
public class WorkspaceForwardController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceForwardController.class);

    /** 下游受信内部机构头（与 workspace 侧解析同义词）。 */
    public static final String INTERNAL_ORG_HEADER = "X-ECOS-Server-Org";
    /** 转发失败机读码。 */
    public static final String CODE_UPSTREAM_UNAVAILABLE = "ECOS-WS-503-UPSTREAM";

    private final RestTemplate restTemplate;
    private final String workspaceBaseUrl;

    public WorkspaceForwardController(RestTemplate restTemplate,
                                      @Value("${ecos.workspace.base-url:http://127.0.0.1:18090}")
                                      String workspaceBaseUrl) {
        this.restTemplate = restTemplate;
        this.workspaceBaseUrl = workspaceBaseUrl.endsWith("/")
                ? workspaceBaseUrl.substring(0, workspaceBaseUrl.length() - 1) : workspaceBaseUrl;
    }

    /**
     * 覆盖场景层全部动词与子路径。
     *
     * <p>除正典 {@code /api/v1/workspace/**} 与其去 v1 兼容形态外，另三条是 D31 补登
     * （2026-11-14 验证批）：{@code ObjectController}/{@code ObjectActionController}/
     * {@code ObjectRelationshipController}/{@code ObjectTimelineController}/{@code ObjectStateMachineController}
     * 与 {@code ObjectQLController}/{@code QueryHistoryController} 都声明在
     * {@code workspace.controller..*} 包内 —— 该包被 {@code GatewayApplication} 整包排除，
     * 而转发面原只覆盖 {@code /api/v1/workspace/**}，导致 {@code #/project_tracker}、
     * {@code #/contract_manager}、{@code #/ontology_workbench} 三个页面取数恒 404
     * （矩阵 V-WS-090~096/050）。四条前缀均为 workspace 独占目录：
     * {@code /api/v1/ecos} 根下另有 buszhi 的 {@code entities}/{@code ontologies} 子族，
     * 故此处只按 {@code objects}/{@code state-machine}/{@code statemachine} 精确子族转发，不碰整根。
     */
    @RequestMapping({"/api/v1/workspace/**", "/api/workspace/**",
            "/api/v1/ecos/objects/**", "/api/ecos/objects/**",
            "/api/v1/ecos/state-machine/**", "/api/ecos/state-machine/**",
            "/api/v1/ecos/statemachine/**", "/api/ecos/statemachine/**",
            "/api/query/**"})
    public void forward(HttpServletRequest request, HttpServletResponse response) throws IOException {
        URI target;
        try {
            String query = request.getQueryString();
            target = URI.create(workspaceBaseUrl + request.getRequestURI() + (query == null ? "" : "?" + query));
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "请求地址非法");
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            for (String name : Collections.list(names)) {
                if (INTERNAL_ORG_HEADER.equalsIgnoreCase(name) || "X-Org-Id".equalsIgnoreCase(name)) {
                    continue;
                }
                headers.addAll(name, Collections.list(request.getHeaders(name)));
            }
        }
        String serverOrg = ServerOrgAttribute.get(request);
        if (serverOrg != null) {
            headers.set(INTERNAL_ORG_HEADER, serverOrg);
        }
        // 信任头还原：EcosTrustHeaderFilter 会在链前无条件剥离 X-ECOS-USER/ROLE/TENANT，
        // 转发到独立 service 时必须从已认证主体重注入，否则 workspace HeaderAuthInterceptor
        // 以 403 ECOS-AUTH-010 拒绝（2026-11-08 活体实证）。缺失即不注入，由下游 fail-closed。
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            headers.set(com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor.H_USER, auth.getName());
            String roles = auth.getAuthorities().stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.joining(","));
            if (!roles.isBlank()) {
                headers.set(com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor.H_ROLE, roles);
            }
            // tenant 取法有先后（D111，2026-11-15 活体实证）：
            // ① 首选 auth.getDetails() 里的 JWT Claims —— 这是 tenant_id 的原始载体，
            //    与 EcosTrustHeaderFilter.reinjectBestEffort 同源取法（该 Filter:166）。
            //    注意**不能**读 ATTR_INJECTED_TENANT：该属性在 Filter 的 finally 里才设置，
            //    而本控制器在 chain.doFilter 内部执行 ⇒ 读到的永远是 null。
            // ② 兜底 TenantContextHolder —— 仅 QuotaFilter 命中时才有值。
            // 原实现只读 ②，导致 /workspace/calibers 这类未被 QuotaFilter 覆盖的路径
            // 出站 tenant 恒空，下游 H_TENANT 必填 ⇒ 403 missing trust headers。
            String tenant = null;
            Object details = auth.getDetails();
            if (details instanceof io.jsonwebtoken.Claims claims) {
                Object t = claims.get("tenant_id");
                if (t != null && !String.valueOf(t).isBlank()) {
                    tenant = String.valueOf(t);
                }
            }
            if (tenant == null || tenant.isBlank()) {
                tenant = com.chinacreator.gzcm.common.context.TenantContextHolder.getTenantId();
            }
            if (tenant != null && !tenant.isBlank()) {
                headers.set(com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor.H_TENANT, tenant);
            }
        }

        byte[] body = request.getInputStream().readAllBytes();
        try {
            ResponseEntity<byte[]> upstream = restTemplate.exchange(
                    target, HttpMethod.valueOf(request.getMethod()),
                    new HttpEntity<>(body.length == 0 ? null : body, headers), byte[].class);
            response.setStatus(upstream.getStatusCode().value());
            String contentType = upstream.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            if (contentType != null) {
                response.setContentType(contentType);
            }
            byte[] payload = upstream.getBody();
            if (payload != null) {
                response.getOutputStream().write(payload);
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // 上游 4xx/5xx 必须原样透传：转发层不得把 405/409/503 折叠成自己的 503
            //（2026-11-08 活体实证：GET pre-validate 的 405 曾被打成 ECOS-WS-503-UPSTREAM）。
            log.info("场景层转发返回错误状态: {} {} -> {}", request.getMethod(), target,
                    e.getStatusCode().value());
            response.setStatus(e.getStatusCode().value());
            String contentType = e.getResponseHeaders() == null ? null
                    : e.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            if (contentType != null) {
                response.setContentType(contentType);
            }
            byte[] payload = e.getResponseBodyAsByteArray();
            if (payload.length > 0) {
                response.getOutputStream().write(payload);
            }
        } catch (RestClientException e) {
            // 占位符须与参数个数一致，否则异常信息被 logback 吞掉（2026-11-13 实测：原 "{} {}" 传三参导致
            // 转发失败的真因在日志里完全看不到，只能看到 "场景层转发失败: PATCH <url>"）。
            log.warn("场景层转发失败: {} {} -> {}: {}", request.getMethod(), target,
                    e.getClass().getSimpleName(), e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":503,\"errorCode\":\"" + CODE_UPSTREAM_UNAVAILABLE
                    + "\",\"message\":\"场景服务不可用（转发失败，fail-closed）\",\"success\":false}");
        }
    }
}
