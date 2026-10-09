package com.chinacreator.gzcm.services.agent.runtime.spi;

import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentEvolutionCapability;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentExecutorCapability;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentMcpCapability;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentMemoryCapability;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentPlannerCapability;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeCapability;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeCapabilityUnavailableException;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentRuntimeProvider;
import com.chinacreator.gzcm.runtime.llm.agent.spi.AgentToolRouterCapability;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentExecutionPlan;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentExecutionResult;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentExecutionTask;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentGoal;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryContext;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryQuery;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryRecord;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentToolSpec;
import com.chinacreator.gzcm.services.agent.runtime.evolution.EvolutionOrchestrator;
import com.chinacreator.gzcm.services.agent.runtime.executor.ExecutorService;
import com.chinacreator.gzcm.services.agent.runtime.mcp.HermesMCPClient;
import com.chinacreator.gzcm.services.agent.runtime.memory.MemoryService;
import com.chinacreator.gzcm.services.agent.runtime.planner.PlannerService;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolRouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 详细设计-06 §八 接缝义务 1 —— {@link AgentRuntimeProvider} 的 provider 侧实现。
 *
 * <p>本类是 {@code services/agent-service} 暴露给智能域的唯一出口：它不新增能力，
 * 只把既有六个 runtime bean 适配成 llm-gateway 的 SPI 契约（铁律 §2.4-7 禁重复实现）。
 * ai-engine 侧 Controller 依赖 SPI，不依赖本类，也不依赖任何 {@code services.agent} 类型。</p>
 *
 * <h3>装配（两种承流形态）</h3>
 * <ul>
 *   <li><b>gateway fat-JAR</b>：{@code GatewayApplication} basePackages 含
 *       {@code com.chinacreator.gzcm.services.agent.runtime}，本类被扫到。</li>
 *   <li><b>aiming service</b>：{@code AimingServiceApplication} basePackages 含
 *       {@code com.chinacreator.gzcm.services.agent}，本类被扫到。</li>
 *   <li><b>两者都没有 agent-service classpath 时</b>（如 {@code ai-engine-boot} 单引擎调试态）：
 *       Spring 拿不到 provider bean，Controller 以 {@code ObjectProvider} 探测后
 *       回显显式错误码，绝不返回 200 + 空数据。</li>
 * </ul>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0（接缝义务 1 依赖方向）
 */
@Component("ecosAgentRuntimeProvider")
public class AgentRuntimeProviderAdapter implements AgentRuntimeProvider {

    /** provider 标识 —— 平台中立，不含厂商/模型名（接缝义务 5 / C255 口径）。 */
    public static final String PROVIDER_ID = "ecos-agent-runtime";

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeProviderAdapter.class);

    private final ObjectProvider<MemoryService> memoryServiceProvider;
    private final ObjectProvider<PlannerService> plannerServiceProvider;
    private final ObjectProvider<ExecutorService> executorServiceProvider;
    private final ObjectProvider<ToolRouterService> toolRouterServiceProvider;
    private final ObjectProvider<HermesMCPClient> mcpClientProvider;
    private final ObjectProvider<EvolutionOrchestrator> evolutionProvider;

    public AgentRuntimeProviderAdapter(
            ObjectProvider<MemoryService> memoryServiceProvider,
            ObjectProvider<PlannerService> plannerServiceProvider,
            ObjectProvider<ExecutorService> executorServiceProvider,
            ObjectProvider<ToolRouterService> toolRouterServiceProvider,
            ObjectProvider<HermesMCPClient> mcpClientProvider,
            ObjectProvider<EvolutionOrchestrator> evolutionProvider) {
        this.memoryServiceProvider = memoryServiceProvider;
        this.plannerServiceProvider = plannerServiceProvider;
        this.executorServiceProvider = executorServiceProvider;
        this.toolRouterServiceProvider = toolRouterServiceProvider;
        this.mcpClientProvider = mcpClientProvider;
        this.evolutionProvider = evolutionProvider;
        log.info("AgentRuntimeProviderAdapter assembled: providerId={}", PROVIDER_ID);
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    // ── MEMORY ─────────────────────────────────────────────────────

    @Override
    public AgentMemoryCapability memory() {
        MemoryService delegate = memoryServiceProvider.getIfAvailable();
        if (delegate == null) {
            throw unavailable(AgentRuntimeCapability.MEMORY,
                    "MemoryService bean not present in this deployment slice");
        }
        return new MemoryCapability(delegate);
    }

    // ── PLANNER ────────────────────────────────────────────────────

    @Override
    public AgentPlannerCapability planner() {
        PlannerService delegate = plannerServiceProvider.getIfAvailable();
        if (delegate == null) {
            throw unavailable(AgentRuntimeCapability.PLANNER,
                    "PlannerService bean not present in this deployment slice");
        }
        return new PlannerCapability(delegate);
    }

    // ── EXECUTOR ───────────────────────────────────────────────────

    @Override
    public AgentExecutorCapability executor() {
        ExecutorService delegate = executorServiceProvider.getIfAvailable();
        if (delegate == null) {
            throw unavailable(AgentRuntimeCapability.EXECUTOR,
                    "ExecutorService bean not present in this deployment slice");
        }
        return new ExecutorCapability(delegate);
    }

    // ── TOOL_ROUTER ────────────────────────────────────────────────

    @Override
    public AgentToolRouterCapability toolRouter() {
        ToolRouterService delegate = toolRouterServiceProvider.getIfAvailable();
        if (delegate == null) {
            throw unavailable(AgentRuntimeCapability.TOOL_ROUTER,
                    "ToolRouterService bean not present in this deployment slice");
        }
        return new ToolRouterCapability(delegate);
    }

    // ── MCP（Hermes = 可选外部 provider，接缝义务 2）────────────────

    @Override
    public AgentMcpCapability mcp() {
        HermesMCPClient delegate = mcpClientProvider.getIfAvailable();
        if (delegate == null) {
            throw unavailable(AgentRuntimeCapability.MCP,
                    "HermesMCPClient bean not present in this deployment slice");
        }
        return new McpCapability(delegate);
    }

    // ── EVOLUTION ──────────────────────────────────────────────────

    @Override
    public AgentEvolutionCapability evolution() {
        EvolutionOrchestrator delegate = evolutionProvider.getIfAvailable();
        if (delegate == null) {
            throw unavailable(AgentRuntimeCapability.EVOLUTION,
                    "EvolutionOrchestrator bean not present in this deployment slice");
        }
        return new EvolutionCapability(delegate);
    }

    private static AgentRuntimeCapabilityUnavailableException unavailable(
            AgentRuntimeCapability capability, String reason) {
        return new AgentRuntimeCapabilityUnavailableException(capability, reason);
    }

    // ── capability 适配器（内部类，不对外暴露 provider 类型）────────

    private static final class MemoryCapability implements AgentMemoryCapability {
        private final MemoryService delegate;

        private MemoryCapability(MemoryService delegate) {
            this.delegate = delegate;
        }

        @Override
        public void store(AgentMemoryRecord record) {
            delegate.store(AgentRuntimeSpiMapping.toProviderRecord(record));
        }

        @Override
        public List<AgentMemoryRecord> retrieve(String agentId, AgentMemoryQuery query) {
            return AgentRuntimeSpiMapping.toSpiRecords(
                    delegate.retrieve(agentId, AgentRuntimeSpiMapping.toProviderQuery(query)));
        }

        @Override
        public AgentMemoryContext buildContext(String agentId, String sessionId) {
            return AgentRuntimeSpiMapping.toSpiContext(delegate.buildContext(agentId, sessionId));
        }
    }

    private static final class PlannerCapability implements AgentPlannerCapability {
        private final PlannerService delegate;

        private PlannerCapability(PlannerService delegate) {
            this.delegate = delegate;
        }

        @Override
        public AgentExecutionPlan createPlan(AgentGoal goal) {
            return AgentRuntimeSpiMapping.toSpiPlan(
                    delegate.createPlan(AgentRuntimeSpiMapping.toProviderGoal(goal)));
        }

        @Override
        public AgentExecutionPlan decompose(AgentExecutionPlan plan) {
            return AgentRuntimeSpiMapping.toSpiPlan(
                    delegate.decompose(AgentRuntimeSpiMapping.toProviderPlan(plan)));
        }
    }

    private static final class ExecutorCapability implements AgentExecutorCapability {
        private final ExecutorService delegate;

        private ExecutorCapability(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override
        public AgentExecutionResult execute(AgentExecutionTask task) {
            return AgentRuntimeSpiMapping.toSpiResult(
                    delegate.execute(AgentRuntimeSpiMapping.toProviderTask(task)));
        }

        @Override
        public AgentExecutionResult executeWithTools(AgentExecutionTask task, List<String> toolIds) {
            return AgentRuntimeSpiMapping.toSpiResult(
                    delegate.executeWithTools(AgentRuntimeSpiMapping.toProviderTask(task), toolIds));
        }
    }

    private static final class ToolRouterCapability implements AgentToolRouterCapability {
        private final ToolRouterService delegate;

        private ToolRouterCapability(ToolRouterService delegate) {
            this.delegate = delegate;
        }

        @Override
        public AgentToolSpec resolveTool(String actionType, Map<String, Object> params) {
            return AgentRuntimeSpiMapping.toSpiTool(delegate.resolveTool(actionType, params));
        }

        @Override
        public List<AgentToolSpec> getAvailableTools(String agentId) {
            return AgentRuntimeSpiMapping.toSpiTools(delegate.getAvailableTools(agentId));
        }

        @Override
        public Object executeTool(String toolCode, Map<String, Object> params) {
            return delegate.executeTool(toolCode, params);
        }
    }

    private static final class McpCapability implements AgentMcpCapability {
        private final HermesMCPClient delegate;

        private McpCapability(HermesMCPClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public String providerId() {
            return "mcp-stdio";
        }

        @Override
        public boolean isAvailable() {
            return delegate.isAvailable();
        }

        @Override
        public String callTool(String toolName, Map<String, Object> args) {
            if (!delegate.isAvailable()) {
                throw unavailable(AgentRuntimeCapability.MCP,
                        "MCP channel not connected; tool '" + toolName + "' was not dispatched");
            }
            return delegate.callTool(toolName, args);
        }
    }

    private static final class EvolutionCapability implements AgentEvolutionCapability {
        private final EvolutionOrchestrator delegate;

        private EvolutionCapability(EvolutionOrchestrator delegate) {
            this.delegate = delegate;
        }

        @Override
        public String triggerEvolution(String trigger, Map<String, Object> context) {
            return delegate.triggerEvolution(trigger, context);
        }

        @Override
        public List<Map<String, Object>> getEvolutionLog(String missionId) {
            return delegate.getEvolutionLog(missionId);
        }
    }
}
