package com.chinacreator.gzcm.runtime.llm.agent.spi;

import java.util.List;
import java.util.Map;

/**
 * 接缝义务 1 —— 进化编排能力（窄接口）。
 *
 * <p>只提升方法签名，不下沉语义：Goal/Mission/Run/Step 的治理流程归分册 10，
 * 本接口是智能域侧的调用出口。调度投递必须走 runtime-task（接缝义务 3 / C237），
 * 本接口不得内建 {@code @Scheduled} 或线程池。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public interface AgentEvolutionCapability {

    /**
     * 触发一轮进化任务。
     *
     * @return missionId
     */
    String triggerEvolution(String trigger, Map<String, Object> context);

    /** 读取某个 mission 的进化日志。 */
    List<Map<String, Object>> getEvolutionLog(String missionId);
}
