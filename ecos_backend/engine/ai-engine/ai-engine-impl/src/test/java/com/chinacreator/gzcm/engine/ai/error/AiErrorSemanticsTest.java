package com.chinacreator.gzcm.engine.ai.error;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.controller.AiErrorAdvice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F06-15 验收 —— 表缺失类故障必须是 5xx，禁被 {@code Exception → 404} 掩蔽（与分册 04
 * {@code KbNavMissingTableReturnsServerError} 同断言形态，防跨册复发）。
 *
 * <p>D.5 码表内没有"内部错误"专用字面量（分册 06 §5.5），持久化面故障按
 * {@link AiErrorClass#TOOL_BACKEND_ERROR}（502 数据服务异常）渲染 —— 它同样是 5xx，
 * 且比 500 更如实地表达"下游数据服务失败"。该就近映射已登记 WP11 偏差台账，
 * 故本测试断言"5xx 且非 404"，而非把状态锁成 500。</p>
 */
class AiErrorSemanticsTest {

    @Test
    @DisplayName("ecos_ai_* 表缺失 → 5xx（502）+ ECOS-AI-* 码，绝不回落 404 伪路由失败")
    void absentTableYields500Not404() throws Exception {
        assertTrue(AiErrorAdvice.class.isAnnotationPresent(RestControllerAdvice.class),
                "智能域必须有局部 advice，否则表缺失会落网关 Exception→404 兜底");
        Method handler = findDataAccessHandler();
        assertNotNull(handler, "AiErrorAdvice 必须声明 @ExceptionHandler(DataAccessException.class)");

        DataAccessException absentTable = new BadSqlGrammarException(
                "SELECT * FROM ecos_ai_agent_run", "SELECT 1",
                new java.sql.SQLSyntaxErrorException(
                        "relation \"ecos_ai_agent_run\" does not exist"));

        ResponseEntity<?> response = invoke(handler, absentTable);
        assertNotNull(response, "处理器必须返回 ResponseEntity");
        int status = response.getStatusCode().value();
        assertTrue(status >= 500 && status < 600,
                "表缺失必须 5xx，实际=" + status + "（禁 404 掩蔽，也禁 200 伪成功）");
        assertNotEquals(404, status, "表缺失绝不映射为 NOT_FOUND（分册 01 接缝的复发形态）");

        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertNotNull(body, "响应体不得为空");
        assertNotNull(body.getErrorCode(), "失败响应必须携带 errorCode");
        assertTrue(body.getErrorCode().startsWith("ECOS-AI-"),
                "errorCode 必须形如 ECOS-AI-*，实际=" + body.getErrorCode());
        assertEquals(AiErrorClass.TOOL_BACKEND_ERROR.errorCode(), body.getErrorCode(),
                "持久化面故障按 TOOL_BACKEND_ERROR 就近归类（D.5 无内部错误类，已登偏差台账）");
        assertNotNull(body.getMessage(), "必须给出用户可读模板消息");
        assertTrue(body.getMessage().contains("诊断码"),
                "消息必须含诊断码字样（要点 4），实际=" + body.getMessage());

        Object data = body.getData();
        assertTrue(data instanceof Map && ((Map<?, ?>) data).containsKey("traceId"),
                "data 必须回带 traceId 供前端定位，实际=" + data);

        Path gatewayFallback = AiSourceScan.locateRepoRoot().resolve(
                "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/GlobalExceptionHandler.java");
        assertTrue(Files.isRegularFile(gatewayFallback),
                "网关兜底 advice 必须仍保留（局部 advice 只抢先命中，不允许删除兜底面）：" + gatewayFallback);
    }

    @Test
    @DisplayName("真实 404 面只留给资源不存在：NOT_FOUND 不参与异常分类兜底")
    void notFoundIsReservedForGenuineAbsentResource() {
        assertEquals(404, AiErrorClass.NOT_FOUND.httpStatus(),
                "NOT_FOUND 是 D.5 里的真实资源不存在面");
        assertNotEquals(AiErrorClass.TOOL_BACKEND_ERROR, AiErrorClass.NOT_FOUND,
                "持久化失败与资源不存在必须是两类，禁互相冒充");
        assertTrue(AiErrorClass.TOOL_BACKEND_ERROR.httpStatus() >= 500,
                "下游数据服务失败必须 5xx（禁被降级为 4xx）");
    }

    private static Method findDataAccessHandler() {
        for (Method method : AiErrorAdvice.class.getDeclaredMethods()) {
            ExceptionHandler handler = method.getAnnotation(ExceptionHandler.class);
            if (handler == null) {
                continue;
            }
            for (Class<?> declared : handler.value()) {
                if (declared.equals(DataAccessException.class)) {
                    return method;
                }
            }
        }
        return null;
    }

    private static ResponseEntity<?> invoke(Method handler, DataAccessException cause) throws Exception {
        handler.setAccessible(true);
        return (ResponseEntity<?>) handler.invoke(
                AiErrorAdvice.class.getDeclaredConstructor().newInstance(), cause);
    }
}
