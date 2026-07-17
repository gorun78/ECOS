package com.chinacreator.gzcm.services.agent.runtime.memory;

import com.chinacreator.gzcm.services.agent.runtime.model.MemoryContext;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryQuery;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryRecord;
import java.util.List;

public interface MemoryService {
    void store(MemoryRecord record);
    List<MemoryRecord> retrieve(String agentId, MemoryQuery query);
    MemoryContext buildContext(String agentId, String sessionId);
}
