package com.chinacreator.gzcm.engine.ai.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.engine.ai.approval.AiOrchestrationErrorCodes;
import com.chinacreator.gzcm.engine.ai.approval.AiOrchestrationException;

/**
 * L3 审批面统一异常出口（原 {@code wagent.web.WAgentOrchestrationAdvice}，2026-11-23 去 wagent 化）。
 *
 * <p><b>作用域刻意收在 {@code assignableTypes = L3ApprovalController}</b>，不抢既有
 * {@code AiErrorAdvice} 的全局面（只增不改）。渲染纪律：</p>
 * <ul>
 *   <li>HTTP 状态与 {@code errorCode} 字面量单源 = {@link AiOrchestrationErrorCodes.Code}（§5.3 冻结）；</li>
 *   <li>消息一律 {@code renderMessage(traceId)}（必含诊断码；{@code E-WA-DEP} 的
 *       {@code {service}} 占位从 extras.service 填充）；</li>
 *   <li><b>禁</b>把异常堆栈/诊断原文回显进响应体（X-49 同类预防）——diagnostic 只进服务端日志；</li>
 *   <li>未分类异常 ⇒ {@code E-INTERNAL 500}（含 traceId，禁裸堆栈）。</li>
 * </ul>
 */
@RestControllerAdvice(assignableTypes = {L3ApprovalController.class})
// gateway 的 GlobalExceptionHandler 有 Exception 兜底且无 @Order，与本源同序时抢在前
// ⇒ 业务异常退化成 500。本源已用 assignableTypes 收窄，提到最高优先级不影响其它控制器。
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class L3ApprovalExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(L3ApprovalExceptionAdvice.class);

    @ExceptionHandler(AiOrchestrationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onOrchestration(AiOrchestrationException e) {
        AiOrchestrationErrorCodes.Code code = e.errorCode();
        String traceId = AiTraceSupport.currentTraceId();
        // 诊断线索只落日志（消息体外泄禁令的服务端对偶面）
        log.warn("[§5.3] L3 审批异常: code={} diagnostic={} traceId={}",
                code.wireCode(), e.getMessage(), traceId);
        String service = e.extras().get("service") == null ? null : String.valueOf(e.extras().get("service"));
        ApiResponse<Map<String, Object>> body = ApiResponse
                .<Map<String, Object>>error(code.httpStatus(), code.wireCode(), code.renderMessage(traceId, service))
                .withTraceId(traceId);
        if (!e.extras().isEmpty()) {
            body.setData(new LinkedHashMap<>(e.extras()));
        }
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onIllegalArgument(IllegalArgumentException e) {
        // 入参形态错误映射 400（禁落 500 掩盖调用方问题）；原文不外泄
        log.warn("[§5.3] L3 审批入参异常: {} traceId={}", e.getMessage(), AiTraceSupport.currentTraceId());
        return render(AiOrchestrationErrorCodes.Code.E_VALIDATION, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onOther(Exception e) {
        String traceId = AiTraceSupport.currentTraceId();
        log.error("[§5.3] L3 审批未分类异常 traceId={}", traceId, e);
        return render(AiOrchestrationErrorCodes.Code.E_INTERNAL, null);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> render(AiOrchestrationErrorCodes.Code code,
                                                                     String service) {
        String traceId = AiTraceSupport.currentTraceId();
        ApiResponse<Map<String, Object>> body = ApiResponse
                .<Map<String, Object>>error(code.httpStatus(), code.wireCode(), code.renderMessage(traceId, service))
                .withTraceId(traceId);
        return ResponseEntity.status(code.httpStatus()).body(body);
    }
}
