package com.chinacreator.gzcm.services.agent.runtime.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Hermes Session Adapter — delegates session persistence to Hermes MCP session_create/session_search.
 *
 * Bean: @Service("ecosHermesSessionAdapter")
 */
@Service("ecosHermesSessionAdapter")
public class HermesSessionAdapter {

    private static final Logger log = LoggerFactory.getLogger(HermesSessionAdapter.class);

    @Autowired(required = false)
    private HermesMCPClient mcpClient;

    /**
     * Create a new session with the given ID and title.
     * @return session info as JSON string, or null if MCP unavailable.
     */
    public String createSession(String sessionId, String title, String profile) {
        if (sessionId == null) sessionId = java.util.UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("title", title != null ? title : "ECOS Session");
        args.put("profile", profile != null ? profile : "ecos-ai-agent");

        if (mcpClient != null && mcpClient.isAvailable()) {
            try {
                String result = mcpClient.callTool("session_create", args);
                log.debug("session_create({}): {}", sessionId, result);
                return "{\"id\":\"" + sessionId + "\",\"title\":\"" + title + "\"}";
            } catch (Exception e) {
                log.warn("session_create via MCP failed: {}", e.getMessage());
            }
        }
        return null; // caller should fallback to ConcurrentHashMap
    }

    /**
     * Search sessions by query string.
     * @return JSON array of matching sessions.
     */
    public String searchSessions(String query, int limit) {
        Map<String, Object> args = new HashMap<>();
        args.put("query", query != null ? query : "");
        args.put("limit", Math.max(1, limit));

        if (mcpClient != null && mcpClient.isAvailable()) {
            try {
                return mcpClient.callTool("session_search", args);
            } catch (Exception e) {
                log.warn("session_search via MCP failed: {}", e.getMessage());
            }
        }
        return "[]";
    }

    public boolean isAvailable() {
        return mcpClient != null && mcpClient.isAvailable();
    }
}
