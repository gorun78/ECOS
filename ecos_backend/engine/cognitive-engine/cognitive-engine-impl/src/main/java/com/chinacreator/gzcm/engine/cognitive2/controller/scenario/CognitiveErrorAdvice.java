package com.chinacreator.gzcm.engine.cognitive2.controller.scenario;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认知域错误矩阵 advice（设计-05 F05-07 / DG / D.5）— 将 guard 异常与 SQL 兜底
 * 转为冻结码响应（{@code code} = HTTP 数值，{@code errorCode} = ECOS-COG-*，traceId 走 MDC 回显）。
 *
 * <p>F05-07 #4：{@code e.getMessage()} 只入日志 + traceId，禁直入响应体。响应 message 一律
 * 取 {@link CognitiveErrorCode#MSG_*} 冻结字面量。</p>
 *
 * <p>2026-10-11 契约核验实修：gateway 宿主内两个 advice 均默认最低优先级时，扫描顺序使
 * {@code gateway.GlobalExceptionHandler} 抢先命中，ECOS-COG-* 矩阵被折叠成 ECOS-SYS-500。
 * 对齐同域既有 {@code ContextBuildErrorAdvice#Order(1)} 先例，本 advice 显式 {@code @Order(1)}
 * 保证局部优先（异常类型互不覆盖，不改任何端点签名）。</p>
 */
@Order(1)
@RestControllerAdvice(basePackages = {"com.chinacreator.gzcm.engine.cognitive2.controller.scenario",
        "com.chinacreator.gzcm.engine.cognitive2.controller"})
public class CognitiveErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(CognitiveErrorAdvice.class);

    @ExceptionHandler(CognitiveGuardException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onGuard(CognitiveGuardException e) {
        log.warn("cognitive guard: code={} msg={}", e.errorCode, e.getMessage());
        Map<String, Object> data = new LinkedHashMap<>();
        String tid = MDC.get("traceId");
        if (tid != null) data.put("traceId", tid);
        data.put("guard", "CognitiveAvailabilityGuard");
        data.put("errorCode", e.errorCode);
        if (e.missing != null) data.put("missing", e.missing);
        // DG-9：显式 stub 面必须带 data.stub=true，前端据此渲染"未实现"占位（禁与真实空态混淆）
        if (CognitiveErrorCode.E503_NOT_IMPLEMENTED.equals(e.errorCode)) data.put("stub", true);
        // CognitiveGuardException 的 message 由 Service 层以 CognitiveErrorCode.MSG_* 冻结字面量构造，
        // 非 driver/DB 原始错误，故可安全回显（保留 "not found" / "deleted" 区分）。
        String frozenMsg = e.getMessage();
        ApiResponse<Map<String, Object>> body =
                ApiResponse.error(e.httpStatus, e.errorCode, frozenMsg);
        body.setData(data);
        return ResponseEntity.status(HttpStatus.valueOf(e.httpStatus)).body(body);
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onSql(SQLException e) {
        log.error("cognitive schema not ready: reason={}", e.getMessage());
        Map<String, Object> data = new LinkedHashMap<>();
        String tid = MDC.get("traceId");
        if (tid != null) data.put("traceId", tid);
        data.put("guard", "CognitiveAvailabilityGuard");
        ApiResponse<Map<String, Object>> body =
                ApiResponse.error(503, CognitiveErrorCode.E501_SCHEMA_NOT_READY, CognitiveErrorCode.MSG_SCHEMA_NOT_READY);
        // D.3 统一错误响应体：data 必须回显（此前构建未挂载，属响应面缺项）
        body.setData(data);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * 必带查询参数缺失（如 E2 的 {@code mind}）→ 400 {@code ECOS-COG-400P}。
     * <p>此前落入下方 catch-all 折叠成 500 {@code ECOS-COG-504} cognitive internal error
     * （2026-11-10 live 实证：{@code GET .../cognition/operation-eval} 无 mind ⇒
     * MissingServletRequestParameterException ⇒ 500），前端因此无法按 4xx/503 区分「参数缺失」
     * 与「引擎故障」，违 D.5「失败语义独立状态码禁折叠」。</p>
     */
    /**
     * 请求体不可解析（畸形 JSON / 类型不匹配）→ 400 {@code ECOS-COG-400P}。
     *
     * <p><b>2026-11-13 验证缺陷 D7</b>：此前 {@code HttpMessageNotReadableException} 落入下方
     * catch-all 折叠成 500 {@code ECOS-COG-504 cognitive internal error}。实测：
     * {@code POST /api/v1/cognitive/executions} 传 {@code {bad json} ⇒ HTTP 500}。
     * 调用方传坏数据是 4xx 而非服务端故障，折叠成 500 会误导排障方向（与上一条
     * {@code onMissingParam} 同一缺陷形态：catch-all 吞掉本可精确归类的异常）。</p>
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException e) {
        log.warn("cognitive unreadable request body: {}", e.getMostSpecificCause().getMessage());
        Map<String, Object> data = new LinkedHashMap<>();
        String tid = MDC.get("traceId");
        if (tid != null) data.put("traceId", tid);
        ApiResponse<Map<String, Object>> body =
                ApiResponse.error(400, CognitiveErrorCode.E400_INVALID_PARAM, CognitiveErrorCode.MSG_INVALID_PARAM);
        body.setData(data);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onMissingParam(MissingServletRequestParameterException e) {
        log.warn("cognitive missing request parameter: name={}", e.getParameterName());
        Map<String, Object> data = new LinkedHashMap<>();
        String tid = MDC.get("traceId");
        if (tid != null) data.put("traceId", tid);
        data.put("missing", e.getParameterName());
        ApiResponse<Map<String, Object>> body =
                ApiResponse.error(400, CognitiveErrorCode.E400_INVALID_PARAM, CognitiveErrorCode.MSG_INVALID_PARAM);
        body.setData(data);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 域内业务校验失败 → 按异常自述状态行输出（一般为 400）。
     *
     * <p><b>2026-11-13 验证缺陷 D37</b>：认知域 Service 用 {@code BusinessException("COG-400: xxx 必填")}
     * 表达入参缺失，但本 advice 没有该类型的处理器，落入下方 catch-all 折叠成 500
     * {@code ECOS-COG-504 cognitive internal error}。实测 {@code POST /api/v1/cognitive/diagnose}、
     * {@code /counterfactual}、{@code /forecast}、{@code /models}、{@code /beliefs}、
     * {@code /hypotheses}、{@code /evidence} 七个写面空体全部 500——调用方传空体是 4xx 而非服务端故障，
     * 与同文件 {@code onMissingParam}/{@code onUnreadableBody} 属同一「catch-all 吞掉可精确归类异常」形态。
     * 状态行取 {@link com.chinacreator.gzcm.common.exception.DataBridgeException#resolveHttpStatus(int)}
     * 单一裁决口径（非法携带值回退 500），不在此处二次判定。</p>
     */
    @ExceptionHandler(com.chinacreator.gzcm.common.exception.DataBridgeException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onDataBridge(
            com.chinacreator.gzcm.common.exception.DataBridgeException e) {
        int status = com.chinacreator.gzcm.common.exception.DataBridgeException
                .resolveHttpStatus(e.getHttpStatus());
        log.warn("cognitive business error: httpStatus={} errorCode={} message={}",
                status, e.getErrorCode(), e.getMessage());
        Map<String, Object> data = new LinkedHashMap<>();
        String tid = MDC.get("traceId");
        if (tid != null) data.put("traceId", tid);
        ApiResponse<Map<String, Object>> body =
                ApiResponse.error(status, e.getMessage());
        body.setData(data);
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onOther(Exception e) {
        log.error("cognitive internal error", e);
        Map<String, Object> data = new LinkedHashMap<>();
        String tid = MDC.get("traceId");
        if (tid != null) data.put("traceId", tid);
        ApiResponse<Map<String, Object>> body =
                ApiResponse.error(500, CognitiveErrorCode.E504_INTERNAL, CognitiveErrorCode.MSG_INTERNAL);
        body.setData(data);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
