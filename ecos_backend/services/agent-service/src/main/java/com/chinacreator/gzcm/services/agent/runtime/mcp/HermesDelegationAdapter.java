package com.chinacreator.gzcm.services.agent.runtime.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Hermes Delegation Adapter — delegates tasks to Hermes MCP delegate_task.
 *
 * Bean: @Service("ecosHermesDelegationAdapter")
 */
@Service("ecosHermesDelegationAdapter")
public class HermesDelegationAdapter {

    private static final Logger log = LoggerFactory.getLogger(HermesDelegationAdapter.class);

    @Autowired(required = false)
    private HermesMCPClient mcpClient;

    /**
     * Delegate a task to a Hermes subagent (fire-and-forget).
     */
    public void delegate(String goal, String context) {
        Map<String, Object> args = new HashMap<>();
        args.put("goal", goal);
        args.put("context", context != null ? context : "");

        if (mcpClient != null && mcpClient.isAvailable()) {
            try {
                String result = mcpClient.callTool("delegate_task", args);
                log.info("delegate_task sent: goal={}, result={}", goal.substring(0, Math.min(50, goal.length())), result);
            } catch (Exception e) {
                log.warn("delegate_task via MCP failed: {}", e.getMessage());
            }
        } else {
            log.warn("MCP unavailable, delegate_task skipped: {}", goal);
        }
    }

    /**
     * Delegate and wait for result with timeout (simulated — MCP delegate is fire-and-forget).
     */
    public String delegateWithTimeout(String goal, String context, long timeoutSeconds) {
        Map<String, Object> args = new HashMap<>();
        args.put("goal", goal);
        args.put("context", context != null ? context : "");

        if (mcpClient != null && mcpClient.isAvailable()) {
            try {
                String result = mcpClient.callTool("delegate_task", args);
                log.info("delegate_task: goal={} → {}", goal.substring(0, Math.min(50, goal.length())), result);
                return result;
            } catch (Exception e) {
                log.warn("delegate_task failed: {}", e.getMessage());
            }
        }
        return "{\"error\":\"MCP unavailable\"}";
    }

    public boolean isAvailable() {
        return mcpClient != null && mcpClient.isAvailable();
    }
}
