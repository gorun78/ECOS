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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * DQ 治理面板子族转发（D105；2026-11-15 活体验证）。
 *
 * <p><b>缺陷形态</b>：{@code services/datanet} 的 {@code DqDashboardController} 映射在裸路径
 * {@code /api/dq}，提供 {@code /dashboard}、{@code /issues}、{@code /executions}、
 * {@code /causal-deviation}、{@code /goal-tracking} 等端点；而
 * {@code VersionPrefixRewriteFilter}（{@code @Order(HIGHEST_PRECEDENCE + 10)}）有一条
 * PMO-48-A 的反向重写 {@code /api/dq/ → /api/v1/dq/}，目标属主是 data-engine 的
 * {@code DqGovernanceController}（只覆盖 {@code /rules} 族）。结果：经网关的
 * {@code /api/dq/goal-tracking} 在 MVC 之前就被改写成 {@code /api/v1/dq/goal-tracking}，
 * <b>两个宿主上都不存在</b>（data-engine 无该端点；datanet 只认裸路径）⇒ 恒 404。
 * 实测矩阵（admin token）：直连 {@code :18082/api/dq/goal-tracking} = <b>200</b>，
 * 经网关 {@code :8080/api/dq/goal-tracking} = <b>404</b>。</p>
 *
 * <p><b>为什么映射的是 v1 形态而不是裸路径</b>：重写过滤器在 MVC <b>之前</b>执行，
 * 连 {@code @Order(HIGHEST_PRECEDENCE + 10)} 都排在 DispatcherServlet 前面 —— 因此到达本控制器的
 * URI 恒为 {@code /api/v1/dq/**}。<b>唯一可拦截的形态是重写后的 v1 形态</b>；
 * 写裸路径的映射永远接不到请求（会被重写拿走）。</p>
 *
 * <p><b>为什么不与 data-engine 抢 {@code /rules}</b>：本类只声明 data-engine
 * <b>没有</b>的子路径（见 {@code DqGovernanceController} 端点清单：
 * {@code /rules} 及其子族）。同前缀共存靠路径不重叠保证——Spring 不会报 Ambiguous mapping，
 * {@code /api/v1/dq/rules} 仍由 data-engine 承接。</p>
 *
 * <p><b>三条纪律</b>（同 {@link GitForwardController} 先例）：① 客户端传入的内部机构头
 * 一律丢弃；② 下游不可达 ⇒ 503 + 机读码，不折叠成 200/404；③ 只转发声明过的端点。</p>
 */
@RestController
public class DqDashboardForwardController {

    private static final Logger log = LoggerFactory.getLogger(DqDashboardForwardController.class);

    /** 转发失败机读码。 */
    public static final String CODE_UPSTREAM_UNAVAILABLE = "ECOS-DQ-503-UPSTREAM";

    private final RestTemplate restTemplate;
    private final String datanetBaseUrl;

    public DqDashboardForwardController(RestTemplate restTemplate,
                                        @Value("${ecos.datanet.base-url:http://127.0.0.1:18082}")
                                        String datanetBaseUrl) {
        this.restTemplate = restTemplate;
        this.datanetBaseUrl = datanetBaseUrl.endsWith("/")
                ? datanetBaseUrl.substring(0, datanetBaseUrl.length() - 1) : datanetBaseUrl;
    }

    /**
     * datanet 独占的 DQ 面板端点（重写后的 v1 形态）。
     *
     * <p>{@code /rules} 族不在此列 —— 它由 data-engine 的 v1 控制器承接。</p>
     */
    @RequestMapping({
            "/api/v1/dq/dashboard",
            "/api/v1/dq/issues",
            "/api/v1/dq/issues/**",
            "/api/v1/dq/executions",
            "/api/v1/dq/executions/**",
            "/api/v1/dq/causal-deviation",
            "/api/v1/dq/goal-tracking",
            "/api/v1/dq/execute-all"
    })
    public void forward(HttpServletRequest request, HttpServletResponse response) throws IOException {
        URI target;
        try {
            String query = request.getQueryString();
            // datanet 侧映射在裸路径 /api/dq/**，故把 v1 形态回退成裸形态再转发
            String path = request.getRequestURI();
            if (path.startsWith("/api/v1/dq/")) {
                path = "/api/dq/" + path.substring("/api/v1/dq/".length());
            }
            target = URI.create(datanetBaseUrl + path + (query == null ? "" : "?" + query));
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "请求地址非法");
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            for (String name : Collections.list(names)) {
                if ("X-ECOS-Server-Org".equalsIgnoreCase(name) || "X-Org-Id".equalsIgnoreCase(name)) {
                    continue;
                }
                headers.addAll(name, Collections.list(request.getHeaders(name)));
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
        } catch (RestClientException e) {
            log.warn("DQ 面板转发失败: {} {}", request.getMethod(), target, e.getMessage());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":503,\"errorCode\":\"" + CODE_UPSTREAM_UNAVAILABLE
                    + "\",\"message\":\"DQ 数据服务不可用（转发失败，fail-closed）\",\"success\":false}");
        }
    }
}
