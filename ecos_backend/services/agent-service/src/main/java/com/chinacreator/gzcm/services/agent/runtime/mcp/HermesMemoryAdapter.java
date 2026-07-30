package com.chinacreator.gzcm.services.agent.runtime.mcp;

import com.chinacreator.gzcm.services.agent.runtime.model.MemoryContext;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryQuery;
import com.chinacreator.gzcm.services.agent.runtime.model.MemoryRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Hermes Memory Adapter — delegates to Hermes MCP memory_store/memory_search.
 * Implements MemoryService so it's a drop-in replacement for MemoryServiceImpl.
 *
 * Bean: @Service("ecosHermesMemoryAdapter")
 */
@Service("ecosHermesMemoryAdapter")
public class HermesMemoryAdapter {

    private static final Logger log = LoggerFactory.getLogger(HermesMemoryAdapter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired(required = false)
    private HermesMCPClient mcpClient;

    public void store(MemoryRecord record) {
        if (record == null) return;
        Map<String, Object> args = new HashMap<>();
        args.put("key", "mem:" + (record.getId() != null ? record.getId() : UUID.randomUUID().toString()));
        args.put("value", record.getContent() != null ? record.getContent() : "");
        args.put("category", record.getLayer() != null ? record.getLayer().name().toLowerCase() : "general");

        if (mcpClient != null && mcpClient.isAvailable()) {
            try {
                String result = mcpClient.callTool("memory_store", args);
                log.debug("memory_store: {}", result);
            } catch (Exception e) {
                log.warn("memory_store via MCP failed, record lost: {}", e.getMessage());
            }
        } else {
            log.debug("MCP unavailable, memory store skipped");
        }
    }

    public List<MemoryRecord> retrieve(String agentId, MemoryQuery query) {
        if (query == null) return Collections.emptyList();
        Map<String, Object> args = new HashMap<>();
        args.put("query", query.getKeywords() != null ? query.getKeywords() : "");
        args.put("limit", query.getTopK() > 0 ? query.getTopK() : 5);

        if (mcpClient != null && mcpClient.isAvailable()) {
            try {
                String result = mcpClient.callTool("memory_search", args);
                return parseRecords(result, query);
            } catch (Exception e) {
                log.warn("memory_search via MCP failed: {}", e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    public MemoryContext buildContext(String agentId, String sessionId) {
        MemoryContext ctx = new MemoryContext();
        ctx.setAgentId(agentId);
        ctx.setSessionId(sessionId);

        MemoryQuery q = new MemoryQuery();
        q.setAgentId(agentId);
        q.setSessionId(sessionId);
        q.setKeywords(sessionId);
        q.setTopK(10);
        List<MemoryRecord> records = retrieve(agentId, q);

        List<MemoryRecord> session = new ArrayList<>();
        List<MemoryRecord> longTerm = new ArrayList<>();
        for (MemoryRecord r : records) {
            String sid = r.getSessionId();
            if (sessionId != null && sessionId.equals(sid)) {
                session.add(r);
            } else {
                longTerm.add(r);
            }
        }
        ctx.setSessionMemory(session);
        ctx.setLongTermMemory(longTerm);
        ctx.setWorkingMemory(session);
        return ctx;
    }

    private List<MemoryRecord> parseRecords(String json, MemoryQuery query) {
        try {
            if (json == null || json.isEmpty()) return Collections.emptyList();
            JsonNode node = mapper.readTree(json);
            if (node.isArray()) {
                List<MemoryRecord> records = new ArrayList<>();
                for (JsonNode item : node) {
                    MemoryRecord r = new MemoryRecord();
                    r.setId(item.has("id") ? item.get("id").asText() : null);
                    r.setContent(item.has("content") ? item.get("content").asText() : item.asText());
                    r.setAgentId(query.getAgentId());
                    r.setSessionId(query.getSessionId());
                    records.add(r);
                }
                return records;
            }
            // Fallback: return text as single record
            MemoryRecord r = new MemoryRecord();
            r.setContent(json);
            r.setAgentId(query.getAgentId());
            r.setSessionId(query.getSessionId());
            return Collections.singletonList(r);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
