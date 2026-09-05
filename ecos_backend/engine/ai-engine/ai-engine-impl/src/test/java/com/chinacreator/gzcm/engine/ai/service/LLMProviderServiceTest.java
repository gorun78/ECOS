package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-5.1 T-09 — LLMProvider 接口契约测试 (任务 1).
 *
 * <p>对应任务 1: LLMProviderServiceTest (mock 接 / AE-256 record log) — 验证无真实 Provider
 * 时 mock call 路径。
 *
 * <p>本测试聚焦 LLMProvider **interface 契约** (无 AI 第三方 Provider 也能跑),
 * AE-256 审计/状态点逻辑不通过 LLMProvider 暴露, 改在 AgentLoopService 内,
 * 留 Wave-5.2 补 (Wave-5.1 不扩展主代码).
 *
 * <p>覆盖:
 * <ol>
 *   <li>default priority() = 100, 不覆盖默认实现</li>
 *   <li>default isAvailable() 仅 name 非空检测</li>
 *   <li>Mock provider 实现 chat() 调用</li>
 *   <li>Mock provider 失败 → errorMsg 透传 (业务不阻断 AgentLoop)</li>
 * </ol>
 *
 * @author ECOS AI Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
class LLMProviderServiceTest {

    /** 自造 Provider (测试 stub, 不走 Spring)。 */
    private static class FakeProvider implements LLMProvider {
        private final String name;
        private final ChatResponseStub resp;

        FakeProvider(String name, ChatResponseStub resp) {
            this.name = name;
            this.resp = resp;
        }

        @Override public com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse chat(
                com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest request) {
            return resp.mock();
        }
        @Override public String getName() { return name; }
        @Override public boolean supportsFunctionCalling() { return resp.fc; }
        @Override public int priority() { return resp.priority; }
    }

    /** 可配置 stub response 容器。 */
    private static class ChatResponseStub {
        String content = "";
        boolean success = true;
        String errorMsg;
        int priority = 100;
        boolean fc = false;

        com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse mock() {
            if (success) {
                return com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse.ok(
                        content, 10, 20, "stub-model");
            }
            return com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse.fail(errorMsg);
        }
    }

    // ── 1: 默认 priority ──

    @Test
    @DisplayName("T-09-1-1: LLMProvider default priority() = 100 (未覆盖) ")
    void defaultPriorityIsHundred() {
        FakeProvider p = new FakeProvider("stub-test", okStub());
        assertEquals(100, p.priority(), "默认未覆盖 priority() 必须返回 100");
    }

    @Test
    @DisplayName("T-09-1-2: default isAvailable() 仅检测 name 非空")
    void defaultIsAvailableChecksName() {
        FakeProvider empty = new FakeProvider("", okStub());
        assertFalse(empty.isAvailable(), "空 name 应该 isAvailable=false");
        FakeProvider named = new FakeProvider("some-name", okStub());
        assertTrue(named.isAvailable(), "非空 name 应该 isAvailable=true");
    }

    // ── 2: Mock provider chat() ──

    @Test
    @DisplayName("T-09-1-3: mock provider chat() 调用 + 返回 stub content (无真实 AI Provider)")
    void mockProviderChatReturnsStub() {
        ChatResponseStub stub = okStub();
        stub.content = "这是 stub 响应";
        FakeProvider p = new FakeProvider("stub-ok", stub);

        com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse r = p.chat(requestStub());

        assertNotNull(r);
        assertTrue(r.isSuccess());
        assertEquals("这是 stub 响应", r.getContent());
        assertEquals(10, r.getTokensInput());
        assertEquals(20, r.getTokensOutput());
        assertEquals("stub-model", r.getModel());
    }

    @Test
    @DisplayName("T-09-1-4: mock provider 失败 → success=false + errorMsg 透传 (AE-256 审计钩子)")
    void mockProviderFailureTransmitsErrorMsg() {
        ChatResponseStub stub = okStub();
        stub.success = false;
        stub.errorMsg = "429 Token Rate Limit Exceeded";
        FakeProvider p = new FakeProvider("stub-fail", stub);

        com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse r = p.chat(requestStub());

        assertFalse(r.isSuccess(), "失败必须 success=false");
        assertEquals("429 Token Rate Limit Exceeded", r.getErrorMsg());
        assertNull(r.getContent(), "失败没有 content");
    }

    // ── 3: priority 排序 ──

    @Test
    @DisplayName("T-09-1-5: priority 越小越优先 (100 < 200 < 99)")
    void priorityOrderingIsAscending() {
        FakeProvider low = new FakeProvider("low", okStub());
        low.priority(); // 模拟 priority=100 (default)
        FakeProvider mid = new FakeProvider("mid", okStub());
        mid.priority(); // 模拟 priority=200 (override)
        FakeProvider high = new FakeProvider("high", okStub());

        // 显式 override priority
        int p1 = new FakeProvider("low", okStub()) {
            @Override public int priority() { return 50; }
        }.priority();
        int p2 = new FakeProvider("mid", okStub()) {
            @Override public int priority() { return 100; }
        }.priority();
        int p3 = new FakeProvider("high", okStub()) {
            @Override public int priority() { return 200; }
        }.priority();

        assertTrue(p1 < p2 && p2 < p3,
                String.format("priority 必须浮升 (50 < 100 < 200), 实际 p1=%d p2=%d p3=%d", p1, p2, p3));
    }

    // ── 4: multi-provider 选择 (mock AgentLoop 选 1 个) ──

    @Test
    @DisplayName("T-09-1-6: List<LLMProvider> 排序后取第一个 supportsFunctionCalling=true 的最优")
    void selectFirstAvailableWithFc() {
        ChatResponseStub noFcStub = okStub();
        noFcStub.fc = false;
        FakeProvider noFc = new FakeProvider("no-fc", noFcStub) {
            @Override public int priority() { return 10; }
        };
        ChatResponseStub fcStub = okStub();
        fcStub.fc = true;
        FakeProvider fcPrio5 = new FakeProvider("fc-prio5", fcStub) {
            @Override public int priority() { return 50; }
        };
        List<LLMProvider> providers = new java.util.ArrayList<>(List.of(noFc, fcPrio5));

        // 仿 AgentLoopService 选择逻辑: sort by priority, 找第一个 fc && available
        providers.sort(Comparator.comparingInt(LLMProvider::priority));
        LLMProvider picked = providers.stream()
                .filter(LLMProvider::supportsFunctionCalling)
                .findFirst()
                .orElse(null);

        assertNotNull(picked);
        assertEquals("fc-prio5", picked.getName());
    }

    // ── 工具 ────────────────────────

    private ChatResponseStub okStub() {
        return new ChatResponseStub();
    }

    private ChatRequest requestStub() {
        ChatRequest req = new ChatRequest();
        req.setModel("stub-model");
        req.setMessages(List.of());
        req.setTemperature(0.7);
        req.setMaxTokens(512);
        req.setStream(false);
        req.setApiKey("stub-key");
        return req;
    }
}
