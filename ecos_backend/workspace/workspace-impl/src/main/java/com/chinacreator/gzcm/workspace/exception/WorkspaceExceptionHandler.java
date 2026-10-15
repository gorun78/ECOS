package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.workspace.scenario.ScenarioDomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 卷07 F07-13 / §四 C-5 —— workspace 面异常 → HTTP 语义唯一映射点。
 *
 * <p>消灭 X-16/X-17 两类假阳性：认知引擎故障与下游不可达一律被吞成 HTTP 200。
 * 本 advice 仅作用于 {@code com.chinacreator.gzcm.workspace..} 控制器，
 * 不改变 gateway / sysman 既有映射面。</p>
 *
 * <p>兜底分支禁止把 {@code e.getMessage()} 写入响应体（X-41 治理）：
 * SQL、连接串、下游 URL 与堆栈原文只进日志，响应体只留固定文案 + traceId。</p>
 */
@RestControllerAdvice(basePackages = "com.chinacreator.gzcm.workspace")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkspaceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceExceptionHandler.class);

    private static final String INTERNAL_MESSAGE = "服务内部错误，请联系管理员并提供追踪号";

    /** C-5 表前 8 行的统一落点（含 scenario 包既有领域异常载体）。 */
    @ExceptionHandler({WorkspaceDomainException.class, ScenarioDomainException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleDomain(Throwable ex) {
        int status;
        String code;
        String message;
        Map<String, Object> details;
        if (ex instanceof WorkspaceDomainException w) {
            status = w.getHttpStatus();
            code = w.getCode();
            message = w.getMessage();
            details = w.getDetails();
        } else {
            ScenarioDomainException s = (ScenarioDomainException) ex;
            status = s.getHttpStatus();
            code = s.getCode();
            message = s.getMessage();
            details = s.getDetails();
        }
        log.warn("workspace 领域异常 code={} http={} traceId={} message={} reason={}",
                code, status, EcosTraceContext.currentTraceId(), ex.getMessage(),
                technicalReason(ex));
        return build(status, code, message, details);
    }

    /** 503 认知/安全引擎不可用的下游原文只落日志面（X-41）；其余领域异常无此字段。 */
    private static String technicalReason(Throwable ex) {
        if (ex instanceof CognitiveEngineUnavailableException c) {
            return c.getTechnicalReason();
        }
        if (ex instanceof com.chinacreator.gzcm.workspace.scenario.CognitiveEngineUnavailableException c) {
            return c.getTechnicalReason();
        }
        if (ex instanceof SecurityEngineUnavailableException s) {
            return s.getTechnicalReason();
        }
        return null;
    }

    /** 请求体校验失败 —— 与 {@link ActionValidationException} 同 code，前端表单逐字段渲染。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleValidation(Exception ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (ex instanceof MethodArgumentNotValidException valid) {
            List<String> missing = valid.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getField).distinct().toList();
            details.put("missingFields", missing);
        }
        return build(400, "ACTION_FIELDS_MISSING", "必填字段缺失或格式非法", details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleBadRequest(Exception ex) {
        return build(400, "BAD_REQUEST", "请求体不可解析或缺少必填参数", Map.of());
    }

    /** 安全引擎/下游 HTTP 层故障 → 500 固定文案，原文不入体（X-41）。 */
    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleDownstream(HttpStatusCodeException ex) {
        log.error("下游 HTTP 失败 status={} traceId={} detail={}",
                ex.getStatusCode().value(), EcosTraceContext.currentTraceId(),
                ex.getStatusText(), ex);
        return build(500, "INTERNAL", INTERNAL_MESSAGE, Map.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleDataAccess(DataAccessException ex) {
        log.error("数据访问异常 traceId={}", EcosTraceContext.currentTraceId(), ex);
        return build(500, "INTERNAL", INTERNAL_MESSAGE, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleUnexpected(Exception ex) {
        log.error("未预期异常 traceId={} type={}",
                EcosTraceContext.currentTraceId(), ex.getClass().getName(), ex);
        return build(500, "INTERNAL", INTERNAL_MESSAGE, Map.of());
    }

    private static ResponseEntity<ApiResponse<Map<String, Object>>> build(
            int httpStatus, String code, String message, Map<String, Object> details) {
        ApiResponse<Map<String, Object>> body =
                ApiResponse.<Map<String, Object>>error(httpStatus, code, message)
                        .withTraceId(EcosTraceContext.currentTraceId());
        if (details != null && !details.isEmpty()) {
            body.setData(details);
        }
        return ResponseEntity.status(HttpStatus.valueOf(httpStatus)).body(body);
    }
}
