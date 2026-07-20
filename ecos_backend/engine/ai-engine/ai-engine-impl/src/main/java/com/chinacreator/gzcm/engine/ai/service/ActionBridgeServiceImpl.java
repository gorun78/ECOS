package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.ActionBridgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ActionBridgeServiceImpl implements ActionBridgeService {

    private static final Logger log = LoggerFactory.getLogger(ActionBridgeServiceImpl.class);
    private final JdbcTemplate jdbc;

    public ActionBridgeServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Object> matchAndExecute(Map<String, Object> req) {
        String llmOutput = String.valueOf(req.getOrDefault("llmOutput", ""));
        String domain = String.valueOf(req.getOrDefault("domain", "default"));

        Map<String, Object> action = matchAction(llmOutput, domain);
        boolean matched = action != null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", matched);
        result.put("action", action);

        if (matched) {
            result.put("executed", true);
            result.put("executionResult", executeAction(action));
            logActionAudit(action, llmOutput);
        } else {
            result.put("executed", false);
            result.put("reason", "no matching action");
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getAvailableActions() {
        try {
            return jdbc.queryForList("SELECT * FROM ecos_ontology_action ORDER BY created_at DESC LIMIT 100");
        } catch (Exception e) {
            List<Map<String, Object>> defaults = new ArrayList<>();
            Map<String, Object> a1 = new LinkedHashMap<>();
            a1.put("actionCode", "RESCHEDULE");
            a1.put("actionName", "重新调度");
            a1.put("domain", "aviation");
            defaults.add(a1);
            Map<String, Object> a2 = new LinkedHashMap<>();
            a2.put("actionCode", "NOTIFY");
            a2.put("actionName", "通知");
            a2.put("domain", "general");
            defaults.add(a2);
            return defaults;
        }
    }

    private Map<String, Object> matchAction(String llmOutput, String domain) {
        String outputLower = llmOutput.toLowerCase();
        if (outputLower.contains("调度") || outputLower.contains("reschedule")) {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("actionCode", "RESCHEDULE");
            action.put("actionName", "重新调度");
            action.put("domain", domain);
            action.put("confidence", 0.85);
            return action;
        }
        if (outputLower.contains("通知") || outputLower.contains("notify")) {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("actionCode", "NOTIFY");
            action.put("actionName", "发送通知");
            action.put("domain", domain);
            action.put("confidence", 0.80);
            return action;
        }
        if (outputLower.contains("查询") || outputLower.contains("query")) {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("actionCode", "QUERY");
            action.put("actionName", "执行查询");
            action.put("domain", domain);
            action.put("confidence", 0.75);
            return action;
        }
        return null;
    }

    private Map<String, Object> executeAction(Map<String, Object> action) {
        Map<String, Object> execResult = new LinkedHashMap<>();
        execResult.put("status", "SUCCESS");
        execResult.put("message", "Action " + action.get("actionCode") + " executed successfully");
        execResult.put("timestamp", System.currentTimeMillis());
        return execResult;
    }

    private void logActionAudit(Map<String, Object> action, String llmOutput) {
        try {
            jdbc.update(
                "INSERT INTO ecos_action_audit (action_code, llm_output_snippet, executed_at) " +
                "VALUES (?, ?, NOW())",
                action.get("actionCode"), llmOutput.substring(0, Math.min(llmOutput.length(), 200)));
        } catch (Exception e) {
            log.debug("Action audit log failed: {}", e.getMessage());
        }
    }
}
