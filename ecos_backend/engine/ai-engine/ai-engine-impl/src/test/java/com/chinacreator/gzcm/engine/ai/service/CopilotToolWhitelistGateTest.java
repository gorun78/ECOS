package com.chinacreator.gzcm.engine.ai.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.chinacreator.gzcm.engine.ai.copilot.CopilotAgentCatalog;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotToolCatalog;
import com.chinacreator.gzcm.engine.ai.tool.ScenarioToolCatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V-COP-031 工具权限：Copilot 智能体的工具面必须受白名单与场景 Agent 双重约束。
 *
 * <p>三道断言各自能变红：</p>
 * <ol>
 *   <li>白名单外工具被拦且执行器零调用（反向对照：白名单内工具必须真的落到执行器）；</li>
 *   <li>场景 Agent 即使把通用兜底工具误配进白名单也必须拦下；</li>
 *   <li>每个 Copilot 智能体的实际白名单 = 其域内工具名全集（外域工具名不得混入，
 *       这是「一个智能体不能越域调另一个域的工具」的代码层判据）。</li>
 * </ol>
 */
@DisplayName("V-COP-031 Copilot 工具权限：白名单 / 场景兜底双闸 + 智能体工具面不越域")
class CopilotToolWhitelistGateTest {

    private static AgentLoopService loopWith(ToolExecutorService executor) {
        AgentLoopService svc = new AgentLoopService();
        ReflectionTestUtils.setField(svc, "toolExecutorService", executor);
        return svc;
    }

    private static ToolExecutorService.ToolResult okResult(String toolName) {
        return ToolExecutorService.ToolResult.ok("c-1", toolName, "{\"ok\":true}", 1L);
    }

    private static AgentLoopConfig configWith(List<String> whitelist) {
        AgentLoopConfig config = new AgentLoopConfig();
        config.setToolWhitelist(whitelist);
        return config;
    }

    private static ToolExecutorService.ToolResult invokeExecuteTool(AgentLoopService svc,
                                                                   ToolCall call,
                                                                   AgentLoopConfig config) {
        return ReflectionTestUtils.invokeMethod(svc, "executeTool", call, config);
    }

    @Test
    @DisplayName("白名单外工具被拦：错误文案指明白名单，执行器一次都没被调用")
    void toolOutsideWhitelistIsBlockedBeforeExecutor() {
        ToolExecutorService executor = mock(ToolExecutorService.class);
        AgentLoopService svc = loopWith(executor);
        AgentLoopConfig config = configWith(List.of("data_list_assets"));

        ToolExecutorService.ToolResult r = invokeExecuteTool(svc,
                new ToolCall("c-9", "security_list_abac_policies", Map.of()), config);

        assertFalse(r.isSuccess(), "跨域工具不得执行");
        assertTrue(r.getError() != null && r.getError().contains("白名单"),
                "拒绝理由须指明白名单，实际: " + r.getError());
        verify(executor, never()).execute(anyString(), any());
    }

    @Test
    @DisplayName("正向对照：白名单内工具必须真的落到执行器（否则上一条「被拦」可恒定成立）")
    void toolInsideWhitelistActuallyReachesExecutor() {
        ToolExecutorService executor = mock(ToolExecutorService.class);
        when(executor.execute(anyString(), any())).thenReturn(okResult("data_list_assets"));
        AgentLoopService svc = loopWith(executor);
        AgentLoopConfig config = configWith(List.of("data_list_assets"));

        ToolExecutorService.ToolResult r = invokeExecuteTool(svc,
                new ToolCall("c-10", "data_list_assets", Map.of()), config);

        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(executor, times(1)).execute(name.capture(), any());
        assertEquals("data_list_assets", name.getValue());
        assertEquals(okResult("data_list_assets").getContent(), r.getContent());
    }

    @Test
    @DisplayName("场景 Agent 即使误配通用兜底工具也拦下（白名单不是唯一判据）")
    void scenarioAgentCannotUseGenericFallbackTools() {
        ToolExecutorService executor = mock(ToolExecutorService.class);
        AgentLoopService svc = loopWith(executor);
        String scenarioTool = ScenarioToolCatalog.all().get(0).toolKey();
        String denied = ScenarioToolCatalog.DENIED_FOR_SCENARIO_AGENTS.stream().findFirst().orElseThrow();
        AgentLoopConfig config = configWith(List.of(scenarioTool, denied));

        ToolExecutorService.ToolResult r = invokeExecuteTool(svc,
                new ToolCall("c-11", denied, Map.of()), config);

        assertFalse(r.isSuccess(), "场景 Agent 不得调用 " + denied);
        assertTrue(r.getError() != null && r.getError().contains("场景 Agent"),
                "拒绝理由须指明场景 Agent 约束，实际: " + r.getError());
        verify(executor, never()).execute(anyString(), any());
    }

    @Test
    @DisplayName("六智能体工具面不越域：每个白名单 = 本域工具名全集，且不含他域工具")
    void eachAgentWhitelistIsExactlyItsOwnDomainTools() {
        Map<String, List<String>> whitelists = CopilotToolCatalog.whitelistsByAgent();
        assertEquals(6, whitelists.size(), "Copilot 内置智能体应为 6 个");

        for (Map.Entry<String, List<String>> entry : whitelists.entrySet()) {
            String agent = entry.getKey();
            List<String> own = CopilotToolCatalog.forAgent(agent).stream()
                    .map(CopilotToolCatalog.Tool::name).toList();
            assertEquals(own, entry.getValue(),
                    agent + " 白名单与其域内工具集不一致（越域或漏登记）");
            for (String name : entry.getValue()) {
                CopilotToolCatalog.Tool tool = CopilotToolCatalog.all().stream()
                        .filter(t -> t.name().equals(name)).findFirst().orElseThrow();
                assertEquals(agent, tool.agent(), "工具 " + name + " 属主智能体越域: " + tool.agent());
            }
        }

        List<String> dataAgent = whitelists.get(CopilotToolCatalog.DATA);
        List<String> securityAgent = whitelists.get(CopilotToolCatalog.SECURITY);
        assertNotEquals(dataAgent, securityAgent, "数据域与安全域工具面不得相同");
        assertFalse(dataAgent.contains("security_list_abac_policies"),
                "数据域智能体不得持有安全域工具");
        assertFalse(securityAgent.contains("data_list_assets"),
                "安全域智能体不得持有数据域工具");
    }

    @Test
    @DisplayName("CopilotAgentCatalog 的 tools() 与工具总表同源（防两处白名单各自漂移）")
    void agentCatalogToolsComeFromSingleSource() {
        for (CopilotAgentCatalog.Definition def : new CopilotAgentCatalog().all()) {
            List<String> own = CopilotToolCatalog.namesForAgent(def.id());
            assertEquals(own, def.tools(),
                    def.id() + " 的系统提示词目录工具集与工具总表不一致");
        }
    }
}
