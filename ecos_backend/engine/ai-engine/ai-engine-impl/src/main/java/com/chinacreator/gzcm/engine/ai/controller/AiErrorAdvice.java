package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.engine.ai.service.ErrorCodeMapper;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能域错误 advice — 详细设计-06 F06-15 要点 1（与分册 04 {@code KbErrorAdvice}、
 * 分册 05 {@code CognitiveErrorAdvice} 同一形态）。
 *
 * <p>控制器层因此<b>禁</b>再写 {@code catch (Exception)}：未预期异常统一在此分类
 * （{@link ErrorCodeMapper#classify}）后渲染为 D.5 冻结码 + 诊断码。</p>
 *
 * <p>三条纪律：</p>
 * <ul>
 *   <li>响应体永不携带异常原文（X-49）；原文只进本类的 ERROR 日志并带 traceId；</li>
 *   <li>持久化/下游故障渲染为 5xx（{@code TOOL_BACKEND_ERROR} 502），<b>绝不</b>回落 404
 *       —— 本 advice 必须先于网关 {@code GlobalExceptionHandler} 命中（局部优先）；</li>
 *   <li>HTTP 状态码取 {@link AiErrorClass#httpStatus()}，与码表一致，禁自造。</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = {"com.chinacreator.gzcm.engine.ai"})
public class AiErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(AiErrorAdvice.class);

    /** D.5 无独立"内部错误"类；持久化面故障按 {@code TOOL_BACKEND_ERROR}（502 数据服务异常）渲染。 */
    private static final String PERSISTENCE_SERVICE = "ai-engine-persistence";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onIllegalArgument(IllegalArgumentException e) {
        return render(ErrorCodeMapper.classify(e, ErrorCodeMapper.Context.generic(),
                AiTraceSupport.currentTraceId()), e);
    }

    /**
     * 表缺失 / SQL 失败（F06-15 验收 {@code AiErrorSemanticsTest#absentTableYields500Not404}）：
     * 必须是 5xx，不得被网关 {@code Exception → 404} 兜底掩蔽成"路由不存在"。
     */
    @ExceptionHandler({DataAccessException.class, SQLException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> onPersistence(Exception e) {
        return render(ErrorCodeMapper.classify(e,
                ErrorCodeMapper.Context.persistence(PERSISTENCE_SERVICE),
                AiTraceSupport.currentTraceId()), e);
    }

    /** 未预期异常：分类后按矩阵渲染；原文只入日志。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onOther(Exception e) {
        return render(ErrorCodeMapper.classify(e, ErrorCodeMapper.Context.generic(),
                AiTraceSupport.currentTraceId()), e);
    }

    /**
     * 真实的"资源不存在"（D.5 {@code NOT_FOUND} 404）。
     *
     * <p>与 {@link #onPersistence} 是同一纪律的两半：服务端故障禁掩蔽为 404，
     * 合法的不存在也禁升级为 502（{@code AgentMetricsCompatController} §T1 验收要求
     * 未知 agentId 出 404）。</p>
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> onNotFound(NotFoundException e) {
        AiError error = AiError.of(AiErrorClass.NOT_FOUND, AiTraceSupport.currentTraceId(),
                e.getMessage());
        log.warn("AI 域资源不存在 → code={} traceId={} reason={}",
                error.errorCode(), error.traceId(), error.rawReason());
        return build(error);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> render(AiError error, Exception cause) {
        log.error("AI 域未处理异常 → class={} code={} traceId={} raw={}",
                error.errorClass().name(), error.errorCode(), error.traceId(), error.rawReason(), cause);
        return build(error);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> build(AiError error) {
        AiErrorClass errorClass = error.errorClass();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errorCode", error.errorCode());
        data.put("errorClass", errorClass.name());
        data.put("traceId", error.traceId() == null ? "-" : error.traceId());
        data.put("retryable", error.retryable());

        ApiResponse<Map<String, Object>> body =
                ApiResponse.error(errorClass.httpStatus(), error.errorCode(), error.userMessage());
        body.setData(data);
        return ResponseEntity.status(HttpStatus.valueOf(errorClass.httpStatus())).body(body);
    }
}
