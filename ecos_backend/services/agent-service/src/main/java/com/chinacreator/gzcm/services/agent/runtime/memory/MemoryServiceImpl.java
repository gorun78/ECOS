package com.chinacreator.gzcm.services.agent.runtime.memory;

import com.chinacreator.gzcm.services.agent.runtime.model.MemoryContext;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryLayer;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryQuery;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MemoryServiceImpl implements MemoryService {
    private static final Logger log = LoggerFactory.getLogger(MemoryServiceImpl.class);
    private final ConcurrentHashMap<String, List<MemoryRecord>> store = new ConcurrentHashMap<>();

    @Override
    public void store(MemoryRecord record) {
        log.debug("Storing memory for agent: {} layer: {}", record.getAgentId(), record.getLayer());
        store.computeIfAbsent(record.getAgentId(), k -> new ArrayList<>()).add(record);
    }

    @Override
    public List<MemoryRecord> retrieve(String agentId, MemoryQuery query) {
        List<MemoryRecord> records = store.getOrDefault(agentId, new ArrayList<>());
        return records.stream()
            .filter(r -> query.getLayer() == null || r.getLayer() == query.getLayer())
            .limit(query.getTopK())
            .collect(Collectors.toList());
    }

    @Override
    public MemoryContext buildContext(String agentId, String sessionId) {
        MemoryContext ctx = new MemoryContext();
        ctx.setAgentId(agentId);
        ctx.setSessionId(sessionId);
        List<MemoryRecord> all = store.getOrDefault(agentId, new ArrayList<>());
        ctx.setWorkingMemory(all.stream().filter(r -> r.getLayer() == MemoryLayer.WORKING).collect(Collectors.toList()));
        ctx.setSessionMemory(all.stream().filter(r -> r.getLayer() == MemoryLayer.SESSION).collect(Collectors.toList()));
        ctx.setLongTermMemory(all.stream().filter(r -> r.getLayer() == MemoryLayer.LONG_TERM).collect(Collectors.toList()));
        ctx.setEnterpriseMemory(all.stream().filter(r -> r.getLayer() == MemoryLayer.ENTERPRISE).collect(Collectors.toList()));
        return ctx;
    }
}
