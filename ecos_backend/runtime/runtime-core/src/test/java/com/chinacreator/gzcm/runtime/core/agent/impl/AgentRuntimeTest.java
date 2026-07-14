package com.chinacreator.gzcm.runtime.core.agent.impl;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.runtime.core.agent.AgentMessage;
import com.chinacreator.gzcm.runtime.core.agent.AgentResult;
import com.chinacreator.gzcm.runtime.core.agent.AgentRuntime;
import com.chinacreator.gzcm.runtime.core.agent.AgentSession;
import com.chinacreator.gzcm.runtime.core.agent.llm.LLMConfig;
import com.chinacreator.gzcm.runtime.core.agent.tool.Tool;
import com.chinacreator.gzcm.runtime.core.agent.tool.ToolResult;

/**
 * Agent 运行时核心功能测试
 */
@DisplayName("AgentRuntime")
class AgentRuntimeTest {

    private AgentRuntime runtime;

    @BeforeEach
    void setUp() {
        LLMConfig config = new LLMConfig();
        config.setProvider(LLMConfig.Provider.OPENAI);
        config.setApiKey("test-key");
        config.setBaseUrl("https://api.openai.com");
        config.setModel("gpt-4");
        runtime = new AgentRuntimeImpl(config);
    }

    // ─── 会话管理 ─────────────────────────────────

    @Nested
    @DisplayName("会话管理")
    class SessionManagementTests {

        @Test
        @DisplayName("创建会话自动生成 ID")
        void createSessionGeneratesId() {
            AgentSession session = runtime.createSession("你是一个助手");
            assertNotNull(session.getId());
            assertFalse(session.getId().isEmpty());
        }

        @Test
        @DisplayName("创建会话自动添加系统消息")
        void createSessionAddsSystemPrompt() {
            AgentSession session = runtime.createSession("你是一个数据分析师");
            assertEquals(1, session.getHistory().size());
            assertEquals(AgentMessage.Role.SYSTEM, session.getHistory().get(0).getRole());
            assertEquals("你是一个数据分析师", session.getHistory().get(0).getContent());
        }

        @Test
        @DisplayName("通过 ID 获取会话")
        void getSessionById() {
            AgentSession session = runtime.createSession("test");
            AgentSession retrieved = runtime.getSession(session.getId());
            assertSame(session, retrieved);
        }

        @Test
        @DisplayName("关闭会话后无法获取")
        void closeSessionRemovesIt() {
            AgentSession session = runtime.createSession("test");
            runtime.closeSession(session.getId());
            assertEquals(0, runtime.getActiveSessionCount());
        }

        @Test
        @DisplayName("默认标题包含 ID 前缀")
        void defaultTitleContainsIdPrefix() {
            AgentSession session = runtime.createSession("system prompt");
            assertNotNull(session.getTitle());
            assertTrue(session.getTitle().contains("Agent Session"));
        }
    }

    // ─── 工具注册表 ─────────────────────────────

    @Nested
    @DisplayName("工具注册表")
    class ToolRegistryTests {

        @Test
        @DisplayName("注册和获取工具")
        void registerAndGetTool() {
            Tool tool = createEchoTool();
            runtime.getToolRegistry().registerTool(tool);

            assertEquals(1, runtime.getToolRegistry().getToolCount());
            assertNotNull(runtime.getToolRegistry().getTool("echo"));
        }

        @Test
        @DisplayName("按子系统获取工具")
        void getToolsBySubsystem() {
            Tool tool1 = createEchoTool();
            Tool tool2 = new Tool() {
                @Override public String getName() { return "data_collect"; }
                @Override public String getDescription() { return "数据采集"; }
                @Override public Map<String, Object> getParametersSchema() { return null; }
                @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success(null, "data_collect", "ok", null, 0); }
                @Override public String getSubsystem() { return "bus-zhi"; }
            };

            runtime.getToolRegistry().registerTools(Arrays.asList(tool1, tool2));
            assertEquals(1, runtime.getToolRegistry().getToolsBySubsystem("runtime").size());
            assertEquals(1, runtime.getToolRegistry().getToolsBySubsystem("bus-zhi").size());
        }

        @Test
        @DisplayName("工具定义列表格式正确")
        void getToolDefinitions() {
            runtime.getToolRegistry().registerTool(createEchoTool());

            List<Map<String, Object>> defs = runtime.getToolRegistry().getToolDefinitions();
            assertEquals(1, defs.size());
            assertTrue(defs.get(0).containsKey("type"));
            assertEquals("function", defs.get(0).get("type"));
        }

        @Test
        @DisplayName("执行已注册工具返回成功")
        void executeRegisteredTool() {
            runtime.getToolRegistry().registerTool(createEchoTool());

            com.chinacreator.gzcm.runtime.core.agent.tool.ToolCall call =
                    new com.chinacreator.gzcm.runtime.core.agent.tool.ToolCall("echo",
                            Map.of("message", "hello"));
            call.setId("tc-001");
            ToolResult result = runtime.getToolRegistry().executeTool(call);

            assertTrue(result.isSuccess());
            assertTrue(result.getContent().contains("hello"));
        }

        @Test
        @DisplayName("执行未注册工具返回错误")
        void executeUnregisteredTool() {
            com.chinacreator.gzcm.runtime.core.agent.tool.ToolCall call =
                    new com.chinacreator.gzcm.runtime.core.agent.tool.ToolCall("unknown",
                            Map.of());
            ToolResult result = runtime.getToolRegistry().executeTool(call);

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("未注册"));
        }
    }

    // ─── 全局统计 ───────────────────────────────

    @Nested
    @DisplayName("全局统计")
    class GlobalStatsTests {

        @Test
        @DisplayName("活跃会话数统计正确")
        void activeSessionCount() {
            assertEquals(0, runtime.getActiveSessionCount());
            runtime.createSession("test1");
            runtime.createSession("test2");
            assertEquals(2, runtime.getActiveSessionCount());
        }

        @Test
        @DisplayName("getGlobalStats 包含关键字段")
        void globalStatsContainsKeyFields() {
            runtime.createSession("test");
            Map<String, Object> stats = runtime.getGlobalStats();

            assertTrue(stats.containsKey("activeSessions"));
            assertTrue(stats.containsKey("totalSessions"));
            assertTrue(stats.containsKey("toolCount"));
            assertTrue(stats.containsKey("model"));
        }
    }

    // ─── AgentSession 详细测试 ──────────────────

    @Nested
    @DisplayName("AgentSession 详细测试")
    class AgentSessionDetailTests {

        @Test
        @DisplayName("消息历史记录正确")
        void messageHistory() {
            AgentSession session = runtime.createSession("system prompt");
            session.addMessage(AgentMessage.user("hello"));
            session.addMessage(AgentMessage.assistant("hi there"));

            assertEquals(3, session.getHistory().size()); // system + user + assistant
            assertEquals(AgentMessage.Role.USER, session.getHistory().get(1).getRole());
        }

        @Test
        @DisplayName("Token 累加正确")
        void tokenAccumulation() {
            AgentSession session = runtime.createSession("test");
            session.addTokens(100);
            session.addTokens(50);
            assertEquals(150, session.getTotalTokens());
        }

        @Test
        @DisplayName("迭代计数正确")
        void iterationCounting() {
            AgentSession session = runtime.createSession("test");
            assertEquals(0, session.getCurrentIteration());
            session.incrementIteration();
            assertEquals(1, session.getCurrentIteration());
        }

        @Test
        @DisplayName("工具调用计数正确")
        void toolCallCounting() {
            AgentSession session = runtime.createSession("test");
            session.incrementToolCallCount();
            session.incrementToolCallCount();
            assertEquals(2, session.getToolCallCount());
        }

        @Test
        @DisplayName("默认最大迭代次数为 10")
        void defaultMaxIterations() {
            AgentSession session = runtime.createSession("test");
            assertEquals(10, session.getMaxIterations());
        }

        @Test
        @DisplayName("closeSession 后会话完成标记")
        void completeSession() {
            AgentSession session = runtime.createSession("test");
            assertFalse(session.isCompleted());
            session.complete();
            assertTrue(session.isCompleted());
        }
    }

    // ─── 工具实现 ───────────────────────────────

    private Tool createEchoTool() {
        return new Tool() {
            @Override
            public String getName() { return "echo"; }

            @Override
            public String getDescription() { return "回显输入消息"; }

            @Override
            public Map<String, Object> getParametersSchema() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> props = new LinkedHashMap<>();
                Map<String, Object> msgProp = new LinkedHashMap<>();
                msgProp.put("type", "string");
                msgProp.put("description", "要回显的消息");
                props.put("message", msgProp);
                schema.put("properties", props);
                schema.put("required", List.of("message"));
                return schema;
            }

            @Override
            public ToolResult execute(Map<String, Object> arguments) {
                String message = (String) arguments.getOrDefault("message", "");
                return ToolResult.success("tc-001", "echo",
                        "回显: " + message, message, 1);
            }
        };
    }
}
