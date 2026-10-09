package com.chinacreator.gzcm.runtime.llm.agent.spi;

import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentExecutionPlan;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentGoal;

/**
 * 接缝义务 1 —— 计划能力（窄接口）。
 *
 * <p>注意职责边界：本接口只做「目标 → 计划」的分解，不构成第二套 Agent Loop。
 * Agent Loop 的唯一实现仍在 ai-engine（{@code AgentLoopService}，接缝义务 2）。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public interface AgentPlannerCapability {

    /** 依据目标生成执行计划。 */
    AgentExecutionPlan createPlan(AgentGoal goal);

    /** 把已有计划进一步拆解为可执行任务。 */
    AgentExecutionPlan decompose(AgentExecutionPlan plan);
}
