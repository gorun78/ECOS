package com.chinacreator.gzcm.engine.ai.arch;

import com.chinacreator.gzcm.engine.ai.service.AgentLoopService;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-05 要点 2 验收：门面归一（X-3 双门面 / X-4 同时注入）。
 *
 * <p>唯一 LLM 出口 = {@link LLMGateway} 的 call + stream（stream 经
 * {@link ChatRequest#getStream()} 表达）；旧 {@link LLMGatewayService} 只准留读接口，
 * 写方法为兼容壳且必须标废弃。</p>
 */
@DisplayName("F06-05 门面归一：call/stream 只在 LLMGateway，旧门面仅读 + 废弃")
class LlmGatewayFacadeTest {

    /** 写路径方法名（新门面独占） */
    private static final Set<String> WRITE_METHOD_NAMES =
            Set.of("call", "callWithRetry", "stream", "chat", "embed");

    @Test
    @DisplayName("F06-05/X-3 call/stream 写路径只声明在新门面 LLMGateway 上")
    void onlyNewFacadeHasCallAndStream() {
        assertNotNull(LLMGateway.class, "新门面必须存在");
        assertTrue(hasMethod(LLMGateway.class, "call", ChatRequest.class),
                "LLMGateway 必须声明 call(ChatRequest)（唯一 LLM 外发口）");
        assertNotNull(findMethod(ChatRequest.class, "getStream"),
                "stream 语义必须由 ChatRequest.stream 表达（SSE 与阻塞共用 call）");

        List<String> violations = new ArrayList<>();
        for (Method m : LLMGatewayService.class.getDeclaredMethods()) {
            if (WRITE_METHOD_NAMES.contains(m.getName())) {
                violations.add("LLMGatewayService#" + m.getName());
            }
        }
        assertTrue(violations.isEmpty(),
                "旧门面不得再声明 LLM 写路径（X-3 双门面）: " + violations);
    }

    @Test
    @DisplayName("F06-05 旧门面整体 + 三个写方法标 @Deprecated(forRemoval)，读接口保留")
    void oldFacadeIsDeprecatedButReadMethodsRemain() {
        assertTrue(LLMGatewayService.class.isAnnotationPresent(Deprecated.class),
                "runtime.llm.LLMGatewayService 必须标 @Deprecated（F06-05 要点 2）");
        assertTrue(LLMGatewayService.class.getAnnotation(Deprecated.class).forRemoval(),
                "兼容壳必须声明 forRemoval，否则删除压力不成立（ARCH_SPEC §七-2 只保 ≥2 迭代）");

        for (String write : List.of("execute", "executeAsync", "executeWithSession")) {
            long deprecated = java.util.Arrays.stream(LLMGatewayService.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(write))
                    .filter(m -> m.isAnnotationPresent(Deprecated.class))
                    .count();
            assertTrue(deprecated >= 1, "旧门面写方法必须逐个标废弃: " + write);
        }
        // 读接口仍在（兼容期内调用方不中断）
        List<String> reads = new ArrayList<>();
        for (Method m : LLMGatewayService.class.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(Deprecated.class)) {
                reads.add(m.getName());
            }
        }
        assertTrue(reads.containsAll(List.of("listProfiles", "getGlobalStats", "getSubsystemStats")),
                "未标废弃的方法必须是读接口: " + reads);
    }

    @Test
    @DisplayName("F06-05/X-4 AgentLoopService 只注入新门面，旧门面字段必须为 0")
    void agentLoopInjectsOnlyNewFacade() {
        List<String> injected = new ArrayList<>();
        for (Field f : AgentLoopService.class.getDeclaredFields()) {
            if (LLMGatewayService.class.equals(f.getType())) {
                injected.add(f.getName());
            }
        }
        assertEquals(List.of(), injected,
                "X-4 双注入未消除：AgentLoopService 仍持有旧门面字段 " + injected);

        boolean hasNew = false;
        for (Field f : AgentLoopService.class.getDeclaredFields()) {
            hasNew |= LLMGateway.class.equals(f.getType());
        }
        assertTrue(hasNew, "AgentLoopService 必须注入 LLMGateway（唯一出口）");
    }

    // ── 工具 ────────────────────────────────────────────────────────────

    private static boolean hasMethod(Class<?> type, String name, Class<?>... params) {
        return findMethod(type, name, params) != null;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
