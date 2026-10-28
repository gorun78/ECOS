package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopResult;
import com.chinacreator.gzcm.engine.ai.service.ErrorCodeMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能域控制器响应渲染单源（详细设计-06 F06-14 / F06-15）。
 *
 * <p>存在理由：控制器不得再自拼异常文案。HTTP 状态一律取 {@link AiErrorClass#httpStatus()}
 * （D.5 码表），响应体只携带 {@code errorCode + errorClass + 渲染消息 + traceId + retryable}，
 * 异常原文永不外泄（X-49），失败态也绝不回落 200 伪成功（用户裁定 A3）或 404（分册 01 接缝）。</p>
 */
public final class AiResponseSupport {

    private AiResponseSupport() {
    }

    /** Loop 终止态 → 带 D.5 状态的响应；成功走 200，失败走分类状态。 */
    public static ResponseEntity<ApiResponse<Map<String, Object>>> fromLoopResult(
            AgentLoopResult result, Map<String, Object> data) {
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(data));
        }
        AiErrorClass errorClass = resolveClass(result);
        ApiResponse<Map<String, Object>> body = ApiResponse.error(
                errorClass.httpStatus(),
                result.getErrorCode() == null ? errorClass.errorCode() : result.getErrorCode(),
                result.getErrorMsg() == null
                        ? errorClass.renderMessage(result.getTraceId()) : result.getErrorMsg());
        body.setData(data);
        return ResponseEntity.status(HttpStatus.valueOf(errorClass.httpStatus())).body(body);
    }

    /**
     * 非 Loop 的失败态（studio 测试/对比/编排等）→ 带 D.5 状态的响应。
     *
     * <p>与 {@link #fromLoopResult} 同一渲染单源：失败绝不回落 200 伪成功（用户裁定 A3），
     * 消息为空时按分类模板渲染，异常原文不外泄（X-49）。</p>
     */
    public static ResponseEntity<ApiResponse<Map<String, Object>>> failure(
            String errorClassName, String errorCode, String message, String traceId,
            Map<String, Object> data) {
        AiErrorClass errorClass = resolveClass(errorClassName);
        String userMessage = message == null || message.isBlank()
                ? errorClass.renderMessage(traceId) : message;
        ApiResponse<Map<String, Object>> body = ApiResponse.error(
                errorClass.httpStatus(),
                errorCode == null || errorCode.isBlank() ? errorClass.errorCode() : errorCode,
                userMessage);
        body.setData(data);
        return ResponseEntity.status(HttpStatus.valueOf(errorClass.httpStatus())).body(body);
    }

    /** 兜底分类：未上报分类的终止态按"下游服务异常"（502）渲染，禁猜文案、禁 200 伪成功。 */
    public static AiErrorClass resolveClass(AgentLoopResult result) {
        return resolveClass(result.getErrorClass());
    }

    /** 未上报或不可识别的分类名 → {@code TOOL_BACKEND_ERROR}（禁自造类、禁 404 掩蔽）。 */
    public static AiErrorClass resolveClass(String errorClassName) {
        AiErrorClass errorClass = ErrorCodeMapper.aiErrorClassOf(errorClassName);
        return errorClass == null ? AiErrorClass.TOOL_BACKEND_ERROR : errorClass;
    }

    /** 按 D.5 分类渲染响应体（带 {@code ECOS-AI-*} 码，禁 {@code internalError} 的 code=-1 无码形态）。 */
    public static <T> ApiResponse<T> classified(AiErrorClass errorClass, String message) {
        return ApiResponse.error(errorClass.httpStatus(), errorClass.errorCode(), message);
    }

    /** 熔断/依赖不可用这类"非 Loop 产出"的失败态：同样走 D.5 状态，不带异常原文。 */
    public static <T> ApiResponse<T> serviceUnavailable(AiErrorClass errorClass, String message) {
        return classified(errorClass, message);
    }

    /**
     * #146x：ai 域「能力尚未上线」501 信封单源（{@code ECOS-AI-501} + {@code data.stub=true}）。
     *
     * <p><b>存在理由</b>：provider 目录面（#146s）与模型目录／模型注册／模型健康三面（#146x，
     * 附录 A 第 28 条）的「未实现」响应必须同形——状态行、机读码、诊断码键面不得分叉
     * （同 #146w 把 cognitive 两枚「未实现」信封收敛为单一工厂的纪律）。机读码与状态行
     * 一律取 {@link AiErrorClass#STUB_CAPABILITY}，禁在本类写 {@code 501}／{@code ECOS-AI-501}
     * 字面量，也禁在控制器里再拼第二套未实现信封。</p>
     *
     * <p>X-22 禁令：目录或载体不可得时禁回落 200＋空 items 伪成功。</p>
     *
     * @param traceId 诊断码（调用方经 {@code AiTraceSupport} 取得，禁自造）
     * @param reason  不可得的机读理由（进对外 message，禁异常原文，X-49）
     * @param extra   面专属取证键（如 {@code missing[]}、如实回显读数）；可为 null，
     *                {@code stub} 标记由本工厂单点写入
     */
    public static ResponseEntity<ApiResponse<Map<String, Object>>> stubCapability(
            String traceId, String reason, Map<String, Object> extra) {
        Map<String, Object> data = new LinkedHashMap<>(
                AiError.of(AiErrorClass.STUB_CAPABILITY, traceId, reason).toResponseBody());
        if (extra != null) {
            data.putAll(extra);
        }
        data.put("stub", true);
        ApiResponse<Map<String, Object>> body = classified(
                AiErrorClass.STUB_CAPABILITY, "该能力尚未上线（" + reason + "）");
        body.setData(data);
        return ResponseEntity.status(HttpStatus.valueOf(AiErrorClass.STUB_CAPABILITY.httpStatus())).body(body);
    }

    /**
     * 依赖未装配（{@code @Autowired(required = false)} 字段为 null）这类启动期失败态。
     *
     * <p>D.5 无"依赖缺失"专用类，就近落 {@code TOOL_BACKEND_ERROR}（502，可重试）；
     * 禁 {@code ApiResponse.internalError} 的 code=-1 无码形态，更禁回落 200 伪成功。</p>
     */
    public static <T> ApiResponse<T> dependencyUnavailable(String service) {
        return serviceUnavailable(AiErrorClass.TOOL_BACKEND_ERROR,
                "依赖服务未就绪: " + (service == null || service.isBlank() ? "ai-engine" : service));
    }
}
