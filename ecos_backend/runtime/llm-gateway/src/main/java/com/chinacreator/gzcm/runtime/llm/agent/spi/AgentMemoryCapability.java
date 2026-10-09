package com.chinacreator.gzcm.runtime.llm.agent.spi;

import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryContext;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryQuery;
import com.chinacreator.gzcm.runtime.llm.agent.spi.model.AgentMemoryRecord;

import java.util.List;

/**
 * 接缝义务 1 —— 记忆能力（窄接口）。
 *
 * <p>契约与 agent-service {@code runtime.memory.MemoryService} 一一对应，
 * 但 DTO 属 llm-gateway SPI 侧独立契约：ai-engine 只依赖本接口，
 * 不再 compile-time 引用 {@code com.chinacreator.gzcm.services.agent.*}（铁律 §2.1）。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public interface AgentMemoryCapability {

    /** 写入一条记忆记录（分层由 {@link AgentMemoryRecord#getLayer()} 决定）。 */
    void store(AgentMemoryRecord record);

    /** 按查询条件检索记忆。 */
    List<AgentMemoryRecord> retrieve(String agentId, AgentMemoryQuery query);

    /** 装配某会话的四层记忆上下文。 */
    AgentMemoryContext buildContext(String agentId, String sessionId);
}
