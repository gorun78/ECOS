package com.chinacreator.gzcm.engine.cognitive2.controller.scenario;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 */
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
