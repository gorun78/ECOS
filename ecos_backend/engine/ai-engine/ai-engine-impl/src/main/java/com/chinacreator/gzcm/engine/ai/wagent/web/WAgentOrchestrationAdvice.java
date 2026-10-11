package com.chinacreator.gzcm.engine.ai.wagent.web;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.engine.ai.wagent.controller.WAgentGoalController;
import com.chinacreator.gzcm.engine.ai.wagent.controller.WAgentQuestionController;
import com.chinacreator.gzcm.engine.ai.wagent.controller.WAgentRunController;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * W Agent 编排面统一异常出口（详细设计-10 §5.3 错误表 → HTTP 状态 + ApiResponse 体）。
 *
 * <p>作用域刻意收在 {@code assignableTypes} 三控制器（W 域新面），<b>不</b>抢既有
 * {@code AiErrorAdvice} 的全局面（只增不改，§5.1）。渲染纪律：</p>
 * <ul>
 *   <li>HTTP 状态与 {@code errorCode} 字面量单源 = {@link WAgentErrorCode}（§5.3 冻结）；</li>
 *   <li>消息一律 {@code renderMessage(traceId)}（必含诊断码；{@code E-WA-DEP} 的
 *       {@code {service}} 占位从 extras.service 填充）；</li>
 *   <li><b>禁</b>把异常堆栈/诊断原文回显进响应体（X-49 同类预防）——
 *       diagnostic 只进服务端日志；</li>
 *   <li>未分类异常 ⇒ {@code E-INTERNAL 500}（含 traceId，禁裸堆栈）。</li>
 * </ul>
 */
@RestControllerAdvice(assignableTypes = {WAgentGoalController.class,
        WAgentQuestionController.class, WAgentRunController.class})
public class WAgentOrchestrationAdvice {

    private static final Logger log = LoggerFactory.getLogger(WAgentOrchestrationAdvice.class);

    @ExceptionHandler(WAgentOrchestrationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onOrchestration(
            WAgentOrchestrationException e) {
        WAgentErrorCode code = e.errorCode();
        String traceId = AiTraceSupport.currentTraceId();
        // 诊断线索只落日志（消息体外泄禁令的服务端对偶面）
        log.warn("[§5.3] W 域编排异常: code={} diagnostic={} traceId={}",
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
        log.warn("[§5.3] W 域入参异常: {} traceId={}", e.getMessage(), AiTraceSupport.currentTraceId());
        return render(WAgentErrorCode.E_VALIDATION, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onOther(Exception e) {
        String traceId = AiTraceSupport.currentTraceId();
        log.error("[§5.3] W 域未分类异常 traceId={}", traceId, e);
        return render(WAgentErrorCode.E_INTERNAL, null);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> render(WAgentErrorCode code, String service) {
        String traceId = AiTraceSupport.currentTraceId();
        ApiResponse<Map<String, Object>> body = ApiResponse
                .<Map<String, Object>>error(code.httpStatus(), code.wireCode(), code.renderMessage(traceId, service))
                .withTraceId(traceId);
        return ResponseEntity.status(code.httpStatus()).body(body);
    }
}
