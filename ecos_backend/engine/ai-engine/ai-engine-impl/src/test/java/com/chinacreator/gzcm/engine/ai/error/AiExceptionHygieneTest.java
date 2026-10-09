package com.chinacreator.gzcm.engine.ai.error;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.controller.AiErrorAdvice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F06-15 验收 —— 智能域控制器层异常卫生（X-49）。
 *
 * <p>两条断言面：</p>
 * <ol>
 *   <li>{@code controllersDeclareNoCatchAll}：带 {@code @RestController} 的类内
 *       {@code catch (Exception …)} / {@code catch (RuntimeException …)} 计数必须为 0，
 *       唯一的兜底捕获位是 {@link AiErrorAdvice}（{@code @RestControllerAdvice}）；</li>
 *   <li>{@code responseBodyNeverContainsRawExceptionMessage}：异常原文既不得出现在控制器
 *       响应构造行里，也不得出现在 {@link AiError} 的响应体 / SSE 帧形态里 —— 原文只入日志。</li>
 * </ol>
 *
 * <p>扫描前经 {@link AiSourceScan#normalize(String)} 抹平注释，避免把文档里"禁写形态"的
 * 引述误判为违规（行号与原文一致）。</p>
 */
class AiExceptionHygieneTest {

    /** 单类型兜底捕获：{@code catch (Exception e)} / {@code catch (final RuntimeException re)}。 */
    private static final Pattern CATCH_ALL =
            Pattern.compile("catch\\s*\\(\\s*(?:final\\s+)?(?:java\\.lang\\.)?(?:Exception|RuntimeException)\\s+\\w+\\s*\\)");

    /** 类型级 Controller 注解（排除 {@code @RestControllerAdvice} —— 那是唯一许可面）。 */
    private static final Pattern CONTROLLER_TYPE =
            Pattern.compile("@(?:Rest)?Controller(?!Advice)");

    /** 响应构造面：一旦出现 {@code getMessage()} 即为原文外泄嫌疑。 */
    private static final Pattern RESPONSE_BUILD = Pattern.compile(
            "ApiResponse\\.\\w+\\s*\\(|put\\(\\s*\"(message|errorMsg|error)\"|Map\\.of\\(\\s*\"(message|errorMsg|error)\"|\\.body\\s*\\(");

    private static final Pattern LOG_CALL = Pattern.compile("\\blog\\.(error|warn|info|debug|trace)\\s*\\(");

    @Test
    @DisplayName("控制器零兜底捕获：catch(Exception/RuntimeException) 计数=0，唯一许可面是 AiErrorAdvice")
    void controllersDeclareNoCatchAll() throws IOException {
        List<Path> controllers = controllerSources();
        assertFalse(controllers.isEmpty(), "智能域控制器目录不应为空（扫描面失效防护）");

        List<String> violations = new ArrayList<>();
        for (Path file : controllers) {
            String text = AiSourceScan.normalize(AiSourceScan.read(file));
            Matcher matcher = CATCH_ALL.matcher(text);
            while (matcher.find()) {
                violations.add(file.getFileName() + " @ 行 "
                        + lineOf(text, matcher.start()) + " → " + matcher.group());
            }
        }
        assertTrue(violations.isEmpty(),
                "F06-15 违规：控制器禁写兜底捕获，异常必须上浮到 AiErrorAdvice " + violations);

        assertTrue(AiErrorAdvice.class.isAnnotationPresent(RestControllerAdvice.class),
                "AiErrorAdvice 必须是 @RestControllerAdvice，才能先于网关 GlobalExceptionHandler 命中");
        RestControllerAdvice advice = AiErrorAdvice.class.getAnnotation(RestControllerAdvice.class);
        assertTrue(List.of(advice.basePackages()).contains("com.chinacreator.gzcm.engine.ai"),
                "advice 作用域必须锁定智能域包，实际=" + List.of(advice.basePackages()));
        assertNotNull(findHandler(Exception.class),
                "advice 必须有 Exception 兜底处理器（捕后必分类，禁裸吞）");
    }

    @Test
    @DisplayName("响应体与 SSE 帧永不携带异常原文：控制器构造面 + AiError/Advice 渲染面双向断言")
    void responseBodyNeverContainsRawExceptionMessage() throws IOException {
        List<String> leaks = new ArrayList<>();
        for (Path file : controllerSources()) {
            String text = AiSourceScan.normalize(AiSourceScan.read(file));
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.contains("getMessage())") && RESPONSE_BUILD.matcher(line).find()
                        && !LOG_CALL.matcher(line).find()) {
                    leaks.add(file.getFileName() + ":" + (i + 1) + " → " + line.trim());
                }
            }
        }
        assertTrue(leaks.isEmpty(),
                "X-49 复发：控制器响应构造行里带上了异常原文 " + leaks);

        // 渲染面：rawReason 是"仅日志"字段，任何对外形态都不得把它带出去
        String sentinel = "PGConnection@127.0.0.1: SECRET-TABLE relation ecos_ai_x does not exist";
        AiError error = new AiError(AiErrorClass.TOOL_BACKEND_ERROR, "trace-leak-check", 1,
                sentinel, "ai-engine-persistence");
        assertNoSentinel(error.toResponseBody(), "AiError.toResponseBody()");
        assertNoSentinel(error.toSseErrorFrame(), "AiError.toSseErrorFrame()");
        assertTrue(error.userMessage().contains("trace-leak-check"),
                "诊断码必须与 MDC traceId 同源并渲染进消息");

        ResponseEntity<ApiResponse<Map<String, Object>>> rendered =
                invokeAdviceHandler(findHandler(java.sql.SQLException.class),
                        new java.sql.SQLSyntaxErrorException(sentinel));
        assertNotNull(rendered.getBody(), "advice 必须渲染响应体");
        assertNoSentinel(rendered.getBody().getMessage(), "AiErrorAdvice 响应 message");
        assertNoSentinel(String.valueOf(rendered.getBody().getData()), "AiErrorAdvice 响应 data");
        assertEquals(sentinel, error.rawReason(), "rawReason 仍须完整保留供日志定位");
    }

    // ── 辅助 ─────────────────────────────────────────────────────────

    private static List<Path> controllerSources() throws IOException {
        List<Path> controllers = new ArrayList<>();
        for (Path file : AiSourceScan.flatJavaFiles(AiSourceScan.aiControllerDir())) {
            String text = AiSourceScan.normalize(AiSourceScan.read(file));
            if (CONTROLLER_TYPE.matcher(text).find()) {
                controllers.add(file);
            }
        }
        return controllers;
    }

    private static void assertNoSentinel(Map<String, Object> payload, String label) {
        assertFalse(String.valueOf(payload).contains("SECRET-TABLE"),
                label + " 泄露了异常原文（X-49）：" + payload);
    }

    private static void assertNoSentinel(String text, String label) {
        assertFalse(text != null && text.contains("SECRET-TABLE"),
                label + " 泄露了异常原文（X-49）：" + text);
    }

    private static Method findHandler(Class<?> exceptionType) {
        for (Method method : AiErrorAdvice.class.getDeclaredMethods()) {
            ExceptionHandler handler = method.getAnnotation(ExceptionHandler.class);
            if (handler == null) {
                continue;
            }
            for (Class<?> declared : handler.value()) {
                if (declared.equals(exceptionType)) {
                    return method;
                }
            }
        }
        return null;
    }

    private static ResponseEntity<ApiResponse<Map<String, Object>>> invokeAdviceHandler(
            Method handler, Throwable cause) {
        assertNotNull(handler, "AiErrorAdvice 缺少对应 @ExceptionHandler");
        handler.setAccessible(true);
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<ApiResponse<Map<String, Object>>> response =
                    (ResponseEntity<ApiResponse<Map<String, Object>>>)
                            handler.invoke(AiErrorAdvice.class.getDeclaredConstructor().newInstance(), cause);
            return response;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 AiErrorAdvice 处理器失败: " + e, e);
        }
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
