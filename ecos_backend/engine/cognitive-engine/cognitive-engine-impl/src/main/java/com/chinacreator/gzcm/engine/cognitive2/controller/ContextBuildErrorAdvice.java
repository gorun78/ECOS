package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.service.context.ContextBuildException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Context Builder 端点错误矩阵 advice（《认知域与智能域补充详细设计 v1.0》§5.5 失败语义）。
 *
 * <p>只接管 {@link ContextBuildException}（context 子域失败族）：
 * 依赖缺失=503 / 权限拒绝=403 / 版本失配=409 / 冲突未解=422 / 超预算=429 / 缺三要素=400，
 * <b>五类失败各自独立 HTTP 状态，禁折叠为 200 + 空 Pack</b>（SUP-CTX-03）。
 * 响应 message 取异常类内冻结字面量；traceId 走 MDC 回显（§12.2）。
 * 用 {@code @Order} 排到 cognitive2 既有 {@code CognitiveErrorAdvice} 之前，
 * 保证本域异常优先命中（两者异常类型不同，互不覆盖）。</p>
 *
 * <p><b>2026-10-11 live 契约核验实修</b>：与 {@code CognitiveErrorAdvice} 同为
 * {@code @Order(1)} 时排序并列，按 bean 注册序 {@code CognitiveErrorAdvice}（basePackages
 * 含本包）先命中，其 catch-all {@code Exception.class} 把 {@code ECOS-COG-400S} 族折叠成
 * 500 {@code ECOS-COG-504}（curl 复现，违 §5.5「五类失败独立状态码禁折叠」）。本 advice
 * 必须严格先于它求值 ⇒ 值取 0（类 javadoc 既有意图的字面落实；assignableTypes 作用域
 * 仅 {@code ContextBuildController}，不影响其余认知端点）。</p>
 */
@Order(0)
@RestControllerAdvice(assignableTypes = ContextBuildController.class)
public class ContextBuildErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(ContextBuildErrorAdvice.class);

    @ExceptionHandler(ContextBuildException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onContextBuildError(ContextBuildException e) {
        log.warn("context build guard: code={} missing={} msg={}",
                e.errorCode, e.missing, e.getMessage());
        Map<String, Object> data = new LinkedHashMap<>();
        String tid = MDC.get("traceId");
        if (tid != null) data.put("traceId", tid);
        data.put("guard", "ContextBuildGuard");
        data.put("errorCode", e.errorCode);
        if (e.missing != null) data.put("missing", e.missing);
        ApiResponse<Map<String, Object>> body =
                ApiResponse.error(e.httpStatus, e.errorCode, e.getMessage());
        body.setData(data);
        body.withTraceIdFromMdc();
        return ResponseEntity.status(HttpStatus.valueOf(e.httpStatus)).body(body);
    }
}
