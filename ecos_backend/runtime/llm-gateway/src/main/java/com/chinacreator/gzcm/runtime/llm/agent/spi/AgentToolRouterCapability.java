package com.chinacreator.gzcm.runtime.llm.agent.spi;

import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentToolSpec;

import java.util.List;
import java.util.Map;

/**
 * 接缝义务 1 + 接缝义务 4 —— 工具路由能力（窄接口）。
 *
 * <p>本期只把既有 {@code runtime.toolrouter.ToolRouterService} 的三个方法提升为 SPI，
 * 不改变行为。接缝义务 4 的 Tool Contract 17 要素登记（替换 lambda 注册）属卷 06 后续
 * 批次，本接口保持 additive 语义：未登记工具不可被 Plan 选用的门禁落地后，
 * {@link #getAvailableTools(String)} 即为该门禁的可见出口。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public interface AgentToolRouterCapability {

    /** 按动作类型 + 参数解析目标工具定义。 */
    AgentToolSpec resolveTool(String actionType, Map<String, Object> params);

    /** 列出某 Agent 可用工具。 */
    List<AgentToolSpec> getAvailableTools(String agentId);

    /** 执行工具（工具自身的权限裁决仍归 security-engine，本接口不复算）。 */
    Object executeTool(String toolCode, Map<String, Object> params);
}
