package com.chinacreator.gzcm.runtime.llm.agent.spi;

/**
 * 详细设计-06 §八 接缝义务 1 的落点 —— Agent 运行时 SPI。
 *
 * <h2>为什么落在 llm-gateway</h2>
 * <p>铁律 §2.1 禁止 engine 侧 import 任何 services 的 impl 类；§2.5 要求横切能力收口到
 * runtime 底座。{@code ai-engine-impl} 与 {@code services/agent-service} 都已
 * compile-time 依赖 {@code runtime/llm-gateway}，因此本接口是二者唯一合法的公共落点
 * （ARCH_SPEC C257 / PRD-01 REQ-PLT-28 / ADR-19 §2.1）。</p>
 *
 * <h2>装配与依赖方向</h2>
 * <ul>
 *   <li>契约（本包）：{@code runtime/llm-gateway}，无 Spring bean、无实现。</li>
 *   <li>实现：{@code services/agent-service} 的 adapter，委派既有
 *       {@code MemoryService}/{@code PlannerService}/{@code ExecutorService}/
 *       {@code ToolRouterService}/{@code HermesMCPClient}/{@code EvolutionOrchestrator} bean。</li>
 *   <li>组装期注入：gateway fat-JAR 与 {@code services/aiming} 通过各自的
 *       {@code @ComponentScan} 得到 adapter bean。</li>
 *   <li>消费方：{@code engine/ai-engine/ai-engine-impl} 的 Controller。</li>
 * </ul>
 *
 * <h2>缺 bean 时的口径（仓库既有裁决）</h2>
 * <p>某承流切片（例如只挂 ai-engine-boot 的调试态）拿不到 provider bean 时，
 * Controller 必须以显式错误码回显「未装配」；拿到 provider 但某能力不可用时，
 * 对应 accessor 抛 {@link AgentRuntimeCapabilityUnavailableException}。
 * <b>两者都禁止降级为 200 + 空数据</b>。</p>
 *
 * <h2>接缝义务 2 的边界</h2>
 * <p>本 SPI 暴露的是「零件」（记忆/计划/单任务执行/工具路由/外部 MCP/进化触发），
 * 不是循环。think→act→observe 的 Agent Loop 全 reactor 唯一实现仍在
 * {@code ai-engine-impl} 的 {@code AgentLoopService}；Hermes 只是可选外部 provider。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0（接缝义务 1）
 */
public interface AgentRuntimeProvider {

    /**
     * provider 标识，仅用于日志/审计与错误体回显。
     * 实现不得返回厂商名或模型名 —— 模型标识的唯一来源是 llm-gateway（接缝义务 5）。
     */
    String providerId();

    /** {@link AgentRuntimeCapability#MEMORY} */
    AgentMemoryCapability memory();

    /** {@link AgentRuntimeCapability#PLANNER} */
    AgentPlannerCapability planner();

    /** {@link AgentRuntimeCapability#EXECUTOR} */
    AgentExecutorCapability executor();

    /** {@link AgentRuntimeCapability#TOOL_ROUTER} */
    AgentToolRouterCapability toolRouter();

    /** {@link AgentRuntimeCapability#MCP} */
    AgentMcpCapability mcp();

    /** {@link AgentRuntimeCapability#EVOLUTION} */
    AgentEvolutionCapability evolution();

    /**
     * 解析能力 accessor，用于统一的能力探测与不可用回显。
     * 默认实现按枚举分派到六个窄接口；新增枚举项时必须在此同步登记。
     *
     * @throws AgentRuntimeCapabilityUnavailableException 能力不支持或暂不可用
     */
    default Object resolve(AgentRuntimeCapability capability) {
        if (capability == null) {
            throw new AgentRuntimeCapabilityUnavailableException(null, "capability is null");
        }
        switch (capability) {
            case MEMORY:
                return memory();
            case PLANNER:
                return planner();
            case EXECUTOR:
                return executor();
            case TOOL_ROUTER:
                return toolRouter();
            case MCP:
                return mcp();
            case EVOLUTION:
                return evolution();
            default:
                throw new AgentRuntimeCapabilityUnavailableException(capability,
                        "provider " + providerId() + " declares no accessor for this capability");
        }
    }

    /**
     * 能力可用性探测：不抛异常，只回答是/否。
     * <p>调用方在得到 false 之后必须走显式不可用分支，禁止静默继续。</p>
     */
    default boolean supports(AgentRuntimeCapability capability) {
        try {
            Object resolved = resolve(capability);
            if (capability == AgentRuntimeCapability.MCP && resolved instanceof AgentMcpCapability) {
                return ((AgentMcpCapability) resolved).isAvailable();
            }
            return resolved != null;
        } catch (AgentRuntimeCapabilityUnavailableException e) {
            return false;
        }
    }

    /**
     * 取不可用原因（用于错误体 {@code reason} 字段）；能力可用时返回 {@code null}。
     */
    default String unavailabilityReason(AgentRuntimeCapability capability) {
        try {
            resolve(capability);
            return null;
        } catch (AgentRuntimeCapabilityUnavailableException e) {
            return e.getReason();
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
