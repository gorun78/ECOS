package com.chinacreator.gzcm.services.agent.runtime.memory;

import com.chinacreator.gzcm.services.agent.runtime.model.MemoryContext;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryLayer;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryQuery;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
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
    private final JdbcTemplate jdbcTemplate;

    public MemoryServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void store(MemoryRecord record) {
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
            jdbcTemplate.update(
                "INSERT INTO ecos_agent.agent_memory (id, agent_id, session_id, layer, content, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                record.getId(),
                record.getAgentId(),
                record.getSessionId(),
                record.getLayer().name(),
                record.getContent(),
                Timestamp.from(record.getTimestamp())
            );
            log.debug("Persisted {} memory to DB for agent: {}", record.getLayer(), record.getAgentId());
        } catch (Exception e) {
            log.warn("Failed to persist memory to DB: {}", e.getMessage());
            store.computeIfAbsent(record.getAgentId(), k -> new ArrayList<>()).add(record);
        }
    }

    private List<MemoryRecord> queryFromDb(String agentId, MemoryQuery query) {
        try {
            return jdbcTemplate.query(
                "SELECT id, agent_id, session_id, layer, content, created_at " +
                "FROM ecos_agent.agent_memory WHERE agent_id = ? AND layer = ? " +
                "ORDER BY created_at DESC LIMIT ?",
                (rs, rowNum) -> {
                    MemoryRecord r = new MemoryRecord();
                    r.setId(rs.getString("id"));
                    r.setAgentId(rs.getString("agent_id"));
                    r.setSessionId(rs.getString("session_id"));
                    r.setLayer(MemoryLayer.valueOf(rs.getString("layer")));
                    r.setContent(rs.getString("content"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) {
                        r.setTimestamp(ts.toInstant());
                    }
                    return r;
                },
                agentId,
                query.getLayer().name(),
                query.getTopK()
            );
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
