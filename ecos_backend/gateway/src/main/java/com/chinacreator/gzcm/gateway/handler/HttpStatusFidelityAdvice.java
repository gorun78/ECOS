package com.chinacreator.gzcm.gateway.handler;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * HTTP 状态行保真：把 {@link ApiResponse#getCode()} 的失败码同步到 HTTP 状态行。
 *
 * <p><b>问题（2026-11-13 验证缺陷 D8）</b>：Controller 直接 {@code return ApiResponse.error(404, ...)}
 * 时，Spring 默认仍以 <b>HTTP 200</b> 返回，只有 body 里 {@code code=404}。客户端（含 fetch/axios/
 * 网关/监控探针）按 HTTP 状态判成功，于是「资源不存在」被当成成功响应。实测抽样 6 个详情端点，
 * 5 个中招（cognitive/hypotheses、cognitive/beliefs、cognitive/models、aip/models、aip/agents），
 * 仅 frameworks 因走 ResponseEntity 而正确。</p>
 *
 * <p><b>修法（用户 2026-11-13 裁决：全局 Advice）</b>：本类在响应体写出前拦截，若 body 是
 * {@link ApiResponse} 且 {@code code} 落在合法 HTTP 状态码区间（100~599），则把状态行改为该码。
 * Controller 代码零改动，全平台一次生效。</p>
 *
 * <p><b>不做映射的两类</b>：</p>
 * <ul>
 *   <li>{@code code == 0}（成功）——保持 200</li>
 *   <li>{@code code < 0}（如 {@code CODE_INTERNAL_ERROR = -1} 的信封 v1 系统错误约定）——
 *       不是合法 HTTP 状态，映射会抛 {@code IllegalArgumentException}。保持原状态行（通常 200），
 *       这类出口由既有棘轮门「只减不增」追缴，不在本类范围</li>
 * </ul>
 *
 * <p><b>不覆盖已有显式状态</b>：若调用方已用 {@code ResponseEntity} 指定了非 200 状态
 * （如 {@code GlobalExceptionHandler} 的 405/404 分支），此处不降级也不改写，只补 200 的缺口。</p>
 */
// 域声明（RestControllerAdviceScopeRatchetTest）：本类实现 ResponseBodyAdvice、不注册任何
// @ExceptionHandler，不参与「两支 advice 争同一异常 ⇒ 命中顺序未定义」的竞争面。但该门是纯静态
// 扫描、无法区分 ResponseBodyAdvice 与异常 advice，故仍显式声明包域（覆盖面不变），
// 而不去放宽该门的 allowlist（保持「只减不增」）。
@RestControllerAdvice(basePackages = "com.chinacreator.gzcm")
public class HttpStatusFidelityAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(HttpStatusFidelityAdvice.class);

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!(body instanceof ApiResponse<?> apiResponse)) {
            return body;
        }
        if (!(response instanceof ServletServerHttpResponse servletResponse)) {
            return body;
        }
        int code = apiResponse.getCode();
        // D45（2026-11-13 验证批）：CODE_INTERNAL_ERROR(-1) 是 ApiResponse 的「服务端内部错误」约定，
        // 不是合法 HTTP 码，此前被下面这行当"负数一律不动"跳过 ⇒ internalError(...) 的 317 处调用
        // 对外全是 HTTP 200 + code=-1（实测 GET /api/v1/system/config/params/{id} 故障即 200）。
        // 现把它显式映射到 500：唯一被特殊对待的负数，其余负数仍保持原状（信封 v1 的其他负数码
        // 尚无对外语义约定，不擅自升格）。
        if (code == ApiResponse.CODE_INTERNAL_ERROR) {
            code = HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        // 只处理「失败 + 合法 HTTP 码」；成功(0) 与其余负数码不动
        if (code <= 0 || code < 100 || code > 599) {
            return body;
        }
        var httpResponse = servletResponse.getServletResponse();
        int current = httpResponse.getStatus();
        // 已有显式非 200 状态（如 handler 已设 405/404）时不覆盖，避免把精准语义改坏
        if (current != HttpStatus.OK.value()) {
            return body;
        }
        try {
            httpResponse.setStatus(code);
        } catch (RuntimeException e) {
            // 非法状态码不应让正常响应变 500，回落原状态并留痕
            log.warn("[HttpStatusFidelity] 无法把 code={} 同步到状态行，保持原状态 {}（{} {}）: {}",
                    code, current, request.getMethod(), request.getURI(), e.getMessage());
        }
        return body;
    }
}
