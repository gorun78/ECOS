package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ExecuteExpertRuleTool implements ToolExecutor {

    private final JdbcTemplate jdbc;

    public ExecuteExpertRuleTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String ruleId = (String) params.get("ruleId");
        Map<String, Object> rule = jdbc.queryForMap(
            "SELECT id, name, rule_type, expression, enabled FROM ecos_expert_rule WHERE id = ?", ruleId);
        Map<String, Object> result = new HashMap<>();
        result.put("ruleId", ruleId);
        result.put("ruleName", rule.get("name"));
        result.put("ruleType", rule.get("rule_type"));
        result.put("status", "executed");
        result.put("evaluatedAt", java.time.Instant.now().toString());
        return result;
    }

    @Override
    public boolean supports(String toolCode) {
        return "ExecuteExpertRule".equals(toolCode);
    }
}
