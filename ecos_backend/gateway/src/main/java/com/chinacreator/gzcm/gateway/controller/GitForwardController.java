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
 * Git 面转发（§2.9.9 G8 送达腿；#147G8）。
 *
 * <p><b>为什么是控制器而不是 Filter</b>：机构值由 {@code ClearanceInterceptor} 在
 * {@code preHandle} 写入（{@link ServerOrgAttribute}），而拦截器在过滤器链**之后**执行；
 * 若在 Filter 里转发，属性必然为空，等于永远不注入内部头。改用控制器后，拦截器先跑、
 * 机构已就位，再转发——时序上天然正确。</p>
 *
 * <p><b>为什么需要转发</b>：{@code /api/v1/ecos/git/**} 的 Controller 在 datanet :18082
 * （gateway 不扫 {@code services.datanet}，本地无映射，实测经网关直查为 404）。</p>
 *
 * <p><b>三条纪律</b>：① 客户端传入的 {@code X-ECOS-Server-Org} / {@code X-Org-Id} 一律丢弃
 * （只允许网关写内部头）；② 机构取 {@link ServerOrgAttribute}（服务端解析值），拿不到就不注入，
 * 由 datanet {@code GitRepoOrgGuard} 按 fail-closed 处置；③ datanet 不可达 ⇒ 503 + 机读码，
 * 绝不把转发失败折叠成 200/404。</p>
 *
 * @author ECOS Gateway #147G8
 */
@RestController
public class GitForwardController {

    private static final Logger log = LoggerFactory.getLogger(GitForwardController.class);

    /** 下游受信内部机构头（与 datanet 守卫同义词）。 */
    public static final String INTERNAL_ORG_HEADER = "X-ECOS-Server-Org";
    /** 转发失败机读码。 */
    public static final String CODE_UPSTREAM_UNAVAILABLE = "ECOS-GIT-503-UPSTREAM";

    private final RestTemplate restTemplate;
    private final String datanetBaseUrl;

    public GitForwardController(RestTemplate restTemplate,
                                @Value("${ecos.datanet.base-url:http://127.0.0.1:18082}") String datanetBaseUrl) {
        this.restTemplate = restTemplate;
        this.datanetBaseUrl = datanetBaseUrl.endsWith("/")
                ? datanetBaseUrl.substring(0, datanetBaseUrl.length() - 1) : datanetBaseUrl;
    }

    /** 覆盖仓库面全部动词与子路径（版本前缀重写前后两种形态）。 */
    @RequestMapping({"/api/v1/ecos/git/**", "/api/ecos/git/**"})
    public void forward(HttpServletRequest request, HttpServletResponse response) throws IOException {
        URI target;
        try {
            String query = request.getQueryString();
            target = URI.create(datanetBaseUrl + request.getRequestURI() + (query == null ? "" : "?" + query));
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
            log.warn("Git 面转发失败: {} {}", request.getMethod(), target, e.getMessage());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":503,\"errorCode\":\"" + CODE_UPSTREAM_UNAVAILABLE
                    + "\",\"message\":\"Git 服务不可用（转发失败，fail-closed）\",\"success\":false}");
        }
    }
}
