package com.chinacreator.gzcm.runtime.llm.agent.spi;

import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentExecutionResult;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentExecutionTask;

import java.util.List;

/**
 * 接缝义务 1 —— 单任务执行能力（窄接口）。
 *
 * <p>与 {@link AgentPlannerCapability} 同属「被 Loop 调用的运行时零件」，
 * 不是第二套 think→act→observe 循环；接缝义务 2 的单一 Loop 口径不受影响。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public interface AgentExecutorCapability {

    /** 执行单个任务。 */
    AgentExecutionResult execute(AgentExecutionTask task);

    /** 限定工具集合执行单个任务。 */
    AgentExecutionResult executeWithTools(AgentExecutionTask task, List<String> toolIds);
}
