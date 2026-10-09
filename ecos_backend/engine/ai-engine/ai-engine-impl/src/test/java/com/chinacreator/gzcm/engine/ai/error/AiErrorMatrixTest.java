package com.chinacreator.gzcm.engine.ai.error;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopService;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F06-14 验收 —— PRD-06 §5.1 七类错误矩阵的四元组（重试上限 / 退避形态 / 用户可读消息模板 /
 * 日志级别）必须逐个登记在 {@link AiErrorClass}（{@code runtime/common-api} 单源），
 * 且 mock-success 构造（X-50）必须彻底离场。
 *
 * <p>纯单元 + 源码扫描，无 Spring 上下文：矩阵值是冻结常量，运行期只有真打流量才走全部分支，
 * 静态断言比"等一条 429"更可靠。</p>
 */
class AiErrorMatrixTest {

    /** PRD §5.1 冻结四元组：类名 → [maxRetries, retryKind, logLevel]。 */
    private static final Map<String, String[]> FROZEN_MATRIX = new LinkedHashMap<>();

    static {
        FROZEN_MATRIX.put("LLM_TIMEOUT", new String[]{"3", "EXPONENTIAL_BACKOFF", "WARN"});
        FROZEN_MATRIX.put("LLM_RATE_LIMITED", new String[]{"3", "EXPONENTIAL_BACKOFF", "WARN"});
        FROZEN_MATRIX.put("PARSE_FAILURE", new String[]{"2", "REGENERATE", "ERROR"});
        FROZEN_MATRIX.put("CONTEXT_OVERFLOW", new String[]{"0", "TRUNCATE_DEGRADE", "WARN"});
        FROZEN_MATRIX.put("GUARDRAIL_DENIED", new String[]{"0", "NONE", "INFO"});
        FROZEN_MATRIX.put("GUARDRAIL_FAIL_CLOSED", new String[]{"0", "NONE", "ERROR"});
        FROZEN_MATRIX.put("TOOL_BACKEND_ERROR", new String[]{"1", "FIXED_BACKOFF", "ERROR"});
    }

    @Test
    @DisplayName("七类逐个承载四元组：码 + 模板（含诊断码）+ 重试上限 + 日志级别，禁 404 掩蔽")
    void sevenClassesEachCarryTemplateRetryBoundAndLevel() {
        assertEquals(7, AiErrorClass.SEVEN_CLASSES.length,
                "PRD §5.1 冻结为七类，禁私自增删矩阵基准");
        Set<String> codes = new HashSet<>();
        for (AiErrorClass errorClass : AiErrorClass.SEVEN_CLASSES) {
            String label = errorClass.name();
            String[] frozen = FROZEN_MATRIX.get(label);
            assertNotNull(frozen, label + " 必须在 PRD §5.1 七类矩阵内");

            assertTrue(errorClass.errorCode().startsWith("ECOS-AI-"),
                    label + " 错误码必须带 ECOS-AI- 前缀（D.5 码表），实际=" + errorClass.errorCode());
            assertTrue(codes.add(errorClass.errorCode()),
                    label + " 错误码重复: " + errorClass.errorCode());
            assertEquals(Integer.parseInt(frozen[0]), errorClass.maxRetries(),
                    label + " 重试上限与 PRD §5.1 不符");
            assertEquals(frozen[1], errorClass.retryKind().name(),
                    label + " 退避形态与 PRD §5.1 不符");
            assertEquals(frozen[2], errorClass.logLevel().name(),
                    label + " 日志级别与 PRD §5.1 不符");

            assertNotNull(errorClass.messageTemplate(), label + " 消息模板不得为空");
            assertTrue(errorClass.messageTemplate().contains("{traceId}"),
                    label + " 消息模板必须含「诊断码 {traceId}」（要点 4，与 MDC 同源）");
            assertFalse(errorClass.httpStatus() == 404,
                    label + " 属七类故障面，禁落 404（分册 01 Exception→404 掩蔽不得复发）");
            assertTrue(errorClass.httpStatus() >= 400 && errorClass.httpStatus() < 600,
                    label + " HTTP 状态必须是 4xx/5xx，实际=" + errorClass.httpStatus());

            String rendered = errorClass.renderMessage("trace-abc-123");
            assertTrue(rendered.contains("trace-abc-123"),
                    label + " 渲染后必须内联诊断码，实际=" + rendered);
            assertFalse(rendered.contains("{") ,
                    label + " 渲染后禁残留占位符，实际=" + rendered);
        }
        // {service} 占位只有 TOOL_BACKEND_ERROR 消费（其余类模板不带服务名）
        String toolMessage = AiErrorClass.TOOL_BACKEND_ERROR.renderMessage("t-1", "kb-service");
        assertTrue(toolMessage.contains("kb-service"),
                "TOOL_BACKEND_ERROR 必须把下游服务名渲染进模板，实际=" + toolMessage);
    }

    @Test
    @DisplayName("重试有界 + 退避单调：上限即冻结界，指数翻倍；降备用 provider 只归 llm-gateway（X-22）")
    void retryIsBoundedAndBackoff() throws IOException {
        for (AiErrorClass errorClass : AiErrorClass.values()) {
            assertTrue(errorClass.maxRetries() <= 3,
                    errorClass.name() + " 重试上限不得超过 PRD §5.1 最大界 3");
            assertFalse(errorClass.canRetry(errorClass.maxRetries()),
                    errorClass.name() + " 到界后必须停止重试（canRetry 与 maxRetries 必须一致）");
            if (errorClass.retryable()) {
                assertTrue(errorClass.canRetry(errorClass.maxRetries() - 1),
                        errorClass.name() + " 到界前最后一次必须仍可重试");
            } else {
                assertFalse(errorClass.canRetry(0),
                        errorClass.name() + " 非重试面必须 0 次即停");
            }
        }

        // 指数退避单调翻倍（LLM_TIMEOUT / LLM_RATE_LIMITED 共用同一形态）
        for (AiErrorClass exp : List.of(AiErrorClass.LLM_TIMEOUT, AiErrorClass.LLM_RATE_LIMITED)) {
            long first = exp.backoffMillis(0);
            long second = exp.backoffMillis(1);
            long third = exp.backoffMillis(2);
            assertTrue(first > 0 && second > first && third > second,
                    exp.name() + " 指数退避必须严格递增，实际=" + first + "/" + second + "/" + third);
            assertEquals(first * 4, third, exp.name() + " 指数退避必须按 2 的幂翻倍");
        }
        assertEquals(800L, AiErrorClass.TOOL_BACKEND_ERROR.backoffMillis(0),
                "TOOL_BACKEND_ERROR 固定退避 800ms");
        assertEquals(800L, AiErrorClass.PARSE_FAILURE.backoffMillis(0),
                "PARSE_FAILURE 重生成固定间隔 800ms（配额与 F06-12 共用，不叠加）");
        assertEquals(0L, AiErrorClass.CONTEXT_OVERFLOW.backoffMillis(0),
                "CONTEXT_OVERFLOW 属截断降级面，不得有退避等待");
        assertEquals(0L, AiErrorClass.GUARDRAIL_DENIED.backoffMillis(0),
                "护栏 DENY 重试等于绕过面，必须 0 等待");

        // X-22：降备用 provider 的开关只能被 llm-gateway 消费，引擎侧禁绕行
        List<Path> offenders = new ArrayList<>();
        long consumers = 0;
        for (Path file : AiSourceScan.mainSources(AiSourceScan.backendRoot())) {
            String text = AiSourceScan.normalize(AiSourceScan.read(file));
            if (!text.contains("fallbackProviderAllowed")) {
                continue;
            }
            consumers++;
            String relative = file.toString().replace('\\', '/');
            boolean declaredInMatrix = relative.contains("runtime/common-api");
            boolean consumedInGateway = relative.contains("runtime/llm-gateway");
            if (!declaredInMatrix && !consumedInGateway) {
                offenders.add(file);
            }
        }
        assertTrue(consumers > 0, "矩阵里的降级开关必须至少被网关消费一次，否则是死码");
        assertTrue(offenders.isEmpty(),
                "X-22 违规：引擎侧/其它域读取了 fallbackProviderAllowed（降 provider 只能经 llm-gateway 内部路由）"
                        + offenders);
        assertEquals(1, countFallbackAllowedFlags(),
                "只有 LLM_RATE_LIMITED 允许降级备用 provider");
    }

    @Test
    @DisplayName("mock-success 彻底离场：buildMockToolResult 0 引用，失败态 content 禁出现成功字样")
    void mockSuccessPathIsGone() throws IOException {
        List<String> hits = new ArrayList<>();
        Pattern successEnvelope = Pattern.compile("\"status\"\\s*:\\s*\"success\"");
        for (Path file : AiSourceScan.mainSources(AiSourceScan.aiEngineMainRoot())) {
            String text = AiSourceScan.normalize(AiSourceScan.read(file));
            if (text.contains("buildMockToolResult")) {
                hits.add("buildMockToolResult@" + file.getFileName());
            }
            if (successEnvelope.matcher(text).find()) {
                hits.add("mock-success-envelope@" + file.getFileName());
            }
        }
        assertTrue(hits.isEmpty(),
                "X-50 复发：成功字样伪造面仍在源码里 " + hits);

        for (Class<?> owner : List.of(AgentLoopService.class, ToolExecutorService.class)) {
            for (Method method : owner.getDeclaredMethods()) {
                assertFalse(method.getName().contains("MockToolResult"),
                        owner.getSimpleName() + " 不得再有 mock 工具结果构造器: " + method.getName());
            }
        }

        ToolExecutorService.ToolResult failure =
                ToolExecutorService.ToolResult.fail("call-1", "kb_query", "下游连接失败", 12L);
        assertFalse(failure.isSuccess(), "工具失败态必须 success=false");
        assertNotNull(failure.getContent(), "失败态必须有 content 供 LLM 侧如实感知");
        assertFalse(failure.getContent().toLowerCase().contains("success"),
                "失败 content 禁出现成功字样（X-50 的机器就是这一行）：" + failure.getContent());
    }

    private static int countFallbackAllowedFlags() {
        int count = 0;
        for (AiErrorClass errorClass : AiErrorClass.values()) {
            if (errorClass.fallbackProviderAllowed()) {
                count++;
            }
        }
        return count;
    }
}
