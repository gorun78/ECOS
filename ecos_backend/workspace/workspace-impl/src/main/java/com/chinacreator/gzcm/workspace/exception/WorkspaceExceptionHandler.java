package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.ScenarioErrorCode;
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
        return build(400, ScenarioErrorCode.CODE_ACTION_FIELDS_MISSING, "必填字段缺失或格式非法", details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleBadRequest(Exception ex) {
        return build(400, "BAD_REQUEST", "请求体不可解析或缺少必填参数", Map.of());
    }

    /**
     * 资源不存在 → 404。common 的 {@link com.chinacreator.gzcm.common.exception.NotFoundException}
     * 不在 WorkspaceDomainException 继承链上，漏映射会被兜底成 500（引用不存在资源的异常场景必须可辨）。
     */
    @ExceptionHandler(com.chinacreator.gzcm.common.exception.NotFoundException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleNotFound(
            com.chinacreator.gzcm.common.exception.NotFoundException ex) {
        log.warn("资源不存在 traceId={} message={}", EcosTraceContext.currentTraceId(), ex.getMessage());
        return build(404, "NOT_FOUND", ex.getMessage(), Map.of());
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

    /**
     * 携带状态行的业务异常（2026-11-13 验证缺陷 D50）。
     *
     * <p><b>缺陷形态（活体实测）</b>：{@code POST /api/v1/workspace/scenarios/sc001/cognitive/diagnose}
     * （及 forecast/simulate/policy 三条同族腿）全部返回
     * {@code 500 {"errorCode":"INTERNAL","message":"服务内部错误…"}}，而 workspace 日志里是
     * {@code BusinessException: no_active_mind_for_scenario=sc001}——抛出点
     * {@code ScenarioCognitiveController#buildPayload} 明明写的是
     * {@code new BusinessException(404, 404, "no_active_mind_for_scenario=" + scenarioId)}。</p>
     *
     * <p><b>根因</b>：本 advice 有 {@code WorkspaceDomainException} / {@code ScenarioDomainException}
     * 两条领域腿，但 {@code BusinessException}（及其父类 {@code DataBridgeException}）不在它们的
     * 继承链上，于是落到 {@code handleUnexpected} 兜底 → 500。「场景还没配 Mind」是<b>调用方
     * 没准备好前置条件</b>，不是服务故障：报 500 会让前端弹「系统错误」并触发 5xx 告警，
     * 真正的修法（先给场景绑定 Mind）永远不会被提示。</p>
     *
     * <p><b>为什么用父类型 {@code DataBridgeException} 而不是 {@code BusinessException}</b>：
     * 兄弟类 {@code NotFoundException} 已单独有 404 腿，这里接父类可以一次覆盖
     * {@code BusinessException} / {@code DataBridgeException} 直接使用者 / 未来同族子类；
     * 状态行取异常自带的 {@code httpStatus}，非法值由 common 单源
     * {@link DataBridgeException#resolveHttpStatus} 回退 500（避免各宿主各写一份区间判据）。</p>
     *
     * <p><b>X-41 不变量</b>：{@code message} 是业务方写给调用方看的（如
     * {@code no_active_mind_for_scenario=sc001}），原样入体；技术栈与堆栈仍只进日志。</p>
     */
    @ExceptionHandler(com.chinacreator.gzcm.common.exception.DataBridgeException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleDataBridge(
            com.chinacreator.gzcm.common.exception.DataBridgeException ex) {
        int status = com.chinacreator.gzcm.common.exception.DataBridgeException
                .resolveHttpStatus(ex.getHttpStatus());
        log.warn("workspace 业务异常 http={} code={} traceId={} message={}",
                status, ex.getErrorCode(), EcosTraceContext.currentTraceId(), ex.getMessage());
        return build(status, String.valueOf(ex.getErrorCode()), ex.getMessage(), Map.of());
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
