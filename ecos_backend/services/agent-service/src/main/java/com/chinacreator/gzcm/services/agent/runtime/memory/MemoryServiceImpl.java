package com.chinacreator.gzcm.services.agent.runtime.memory;

import com.chinacreator.gzcm.services.agent.dao.AgentSessionDao;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryContext;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryLayer;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryQuery;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MemoryServiceImpl implements MemoryService {
    private static final Logger log = LoggerFactory.getLogger(MemoryServiceImpl.class);
    private final ConcurrentHashMap<String, List<MemoryRecord>> store = new ConcurrentHashMap<>();
    private final AgentSessionDao sessionDao;

    @Autowired(required = false)
    @Qualifier("ecosHermesMemoryAdapter")
    private MemoryService hermesMemoryAdapter;

    public MemoryServiceImpl(AgentSessionDao sessionDao) {
        this.sessionDao = sessionDao;
    }

    @Override
    public void store(MemoryRecord record) {
        try {
            if (hermesMemoryAdapter != null) {
                hermesMemoryAdapter.store(record);
                return;
            }
        } catch (Exception e) {
            log.warn("Hermes Memory store fallback: {}", e.getMessage());
        }
        log.debug("Storing memory for agent: {} layer: {}", record.getAgentId(), record.getLayer());
        if (record.getId() == null) {
            record.setId(UUID.randomUUID().toString());
        }
        if (record.getTimestamp() == null) {
            record.setTimestamp(Instant.now());
        }
        if (record.getLayer() == MemoryLayer.WORKING || record.getLayer() == MemoryLayer.SESSION) {
            store.computeIfAbsent(record.getAgentId(), k -> new ArrayList<>()).add(record);
        }
        if (record.getLayer() == MemoryLayer.LONG_TERM || record.getLayer() == MemoryLayer.ENTERPRISE) {
            persistToDb(record);
        }
    }

    @Override
    public List<MemoryRecord> retrieve(String agentId, MemoryQuery query) {
        try {
            if (hermesMemoryAdapter != null) {
                return hermesMemoryAdapter.retrieve(agentId, query);
            }
        } catch (Exception e) {
            log.warn("Hermes Memory retrieve fallback: {}", e.getMessage());
        }
        if (query.getLayer() == MemoryLayer.LONG_TERM || query.getLayer() == MemoryLayer.ENTERPRISE) {
            return queryFromDb(agentId, query);
        }
        List<MemoryRecord> records = store.getOrDefault(agentId, new ArrayList<>());
        return records.stream()
            .filter(r -> query.getLayer() == null || r.getLayer() == query.getLayer())
            .limit(query.getTopK())
            .collect(Collectors.toList());
    }

    @Override
    public MemoryContext buildContext(String agentId, String sessionId) {
        try {
            if (hermesMemoryAdapter != null) {
                return hermesMemoryAdapter.buildContext(agentId, sessionId);
            }
        } catch (Exception e) {
            log.warn("Hermes Memory buildContext fallback: {}", e.getMessage());
        }
        MemoryContext ctx = new MemoryContext();
        ctx.setAgentId(agentId);
        ctx.setSessionId(sessionId);
        List<MemoryRecord> inMemory = store.getOrDefault(agentId, new ArrayList<>());
        ctx.setWorkingMemory(inMemory.stream().filter(r -> r.getLayer() == MemoryLayer.WORKING).collect(Collectors.toList()));
        ctx.setSessionMemory(inMemory.stream().filter(r -> r.getLayer() == MemoryLayer.SESSION).collect(Collectors.toList()));
        MemoryQuery ltQuery = new MemoryQuery();
        ltQuery.setLayer(MemoryLayer.LONG_TERM);
        ltQuery.setTopK(20);
        ctx.setLongTermMemory(queryFromDb(agentId, ltQuery));
        MemoryQuery entQuery = new MemoryQuery();
        entQuery.setLayer(MemoryLayer.ENTERPRISE);
        entQuery.setTopK(20);
        ctx.setEnterpriseMemory(queryFromDb(agentId, entQuery));
        return ctx;
    }

    private void persistToDb(MemoryRecord record) {
        try {
            sessionDao.insertMemory(
                record.getId(),
                record.getAgentId(),
                record.getSessionId(),
                record.getLayer().name(),
                record.getContent(),
                record.getTimestamp()
            );
            log.debug("Persisted {} memory to DB for agent: {}", record.getLayer(), record.getAgentId());
        } catch (Exception e) {
            log.warn("Failed to persist memory to DB: {}", e.getMessage());
            store.computeIfAbsent(record.getAgentId(), k -> new ArrayList<>()).add(record);
        }
    }

    private List<MemoryRecord> queryFromDb(String agentId, MemoryQuery query) {
        try {
            return sessionDao.listMemory(agentId, query.getLayer().name(), query.getTopK());
        } catch (Exception e) {
            log.warn("Failed to query memory from DB, falling back to in-memory: {}", e.getMessage());
            List<MemoryRecord> records = store.getOrDefault(agentId, new ArrayList<>());
            return records.stream()
                .filter(r -> r.getLayer() == query.getLayer())
                .limit(query.getTopK())
                .collect(Collectors.toList());
        }
    }
}
