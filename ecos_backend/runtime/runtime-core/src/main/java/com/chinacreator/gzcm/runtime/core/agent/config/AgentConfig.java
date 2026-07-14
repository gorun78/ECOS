package com.chinacreator.gzcm.runtime.core.agent.config;

import com.chinacreator.gzcm.runtime.core.agent.AgentRuntime;
import com.chinacreator.gzcm.runtime.core.agent.impl.AgentRuntimeImpl;
import com.chinacreator.gzcm.runtime.core.agent.impl.MockLLMClient;
import com.chinacreator.gzcm.runtime.core.agent.impl.ToolRegistryImpl;
import com.chinacreator.gzcm.runtime.core.agent.llm.LLMClient;
import com.chinacreator.gzcm.runtime.core.agent.llm.LLMConfig;
import com.chinacreator.gzcm.runtime.core.agent.tool.ToolRegistry;
import com.chinacreator.gzcm.runtime.core.agent.tool.impl.KnowledgeSearchTool;
import com.chinacreator.gzcm.runtime.core.agent.tool.impl.ObjectQueryTool;
import com.chinacreator.gzcm.runtime.core.agent.tool.impl.OntologyExploreTool;
import com.chinacreator.gzcm.runtime.core.agent.tool.impl.WorkflowStartTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public ToolRegistry toolRegistry() {
        ToolRegistryImpl registry = new ToolRegistryImpl();
        registry.registerTool(new KnowledgeSearchTool());
        registry.registerTool(new ObjectQueryTool());
        registry.registerTool(new OntologyExploreTool());
        registry.registerTool(new WorkflowStartTool());
        return registry;
    }

    @Bean
    public LLMClient llmClient() {
        return new MockLLMClient();
    }

    @Bean
    public AgentRuntime agentRuntime(ToolRegistry toolRegistry, LLMClient llmClient) {
        LLMConfig config = new LLMConfig();
        config.setProvider(LLMConfig.Provider.DEEPSEEK);
        config.setModel("deepseek-chat");
        return new AgentRuntimeImpl(config, llmClient, toolRegistry);
    }
}
