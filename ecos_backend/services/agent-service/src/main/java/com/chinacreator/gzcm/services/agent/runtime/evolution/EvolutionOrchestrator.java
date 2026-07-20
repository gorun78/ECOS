package com.chinacreator.gzcm.services.agent.runtime.evolution;

import com.chinacreator.gzcm.services.agent.runtime.executor.ExecutorService;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EvolutionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EvolutionOrchestrator.class);

    private static final List<String> EVOLUTION_STAGES = List.of(
        "DIAGNOSIS", "ONTOLOGY_EVOLVE", "KNOWLEDGE_REBUILD", "SECURITY_HEAL", "DEPLOYMENT"
    );

    private static final Map<String, String> STAGE_AGENT_MAP = Map.of(
        "DIAGNOSIS", "agent-data",
        "ONTOLOGY_EVOLVE", "agent-ontology",
        "KNOWLEDGE_REBUILD", "agent-knowledge",
        "SECURITY_HEAL", "agent-security",
        "DEPLOYMENT", "agent-scenario"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ExecutorService executorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvolutionOrchestrator(JdbcTemplate jdbcTemplate, ExecutorService executorService) {
        this.jdbcTemplate = jdbcTemplate;
        this.executorService = executorService;
    }

    public String triggerEvolution(String trigger, Map<String, Object> context) {
        String missionId = UUID.randomUUID().toString();
        log.info("Triggering evolution mission: {} trigger: {}", missionId, trigger);

        for (String stage : EVOLUTION_STAGES) {
            String logId = UUID.randomUUID().toString();
            String agentId = STAGE_AGENT_MAP.get(stage);

            try {
                String contextJson = objectMapper.writeValueAsString(context);
                jdbcTemplate.update(
                    "INSERT INTO ecos_ai.ecos_evolution_log (id, mission_id, stage, agent_id, input_context, status, started_at) VALUES (?,?,?,?,?::jsonb,'STARTED',NOW())",
                    logId, missionId, stage, agentId, contextJson
                );
            } catch (Exception e) {
                log.warn("Failed to insert evolution log for stage {}: {}", stage, e.getMessage());
                continue;
            }

            try {
                ExecutionTask task = new ExecutionTask();
                task.setId(UUID.randomUUID().toString());
                task.setInstruction("Evolution stage: " + stage + " for trigger: " + trigger);
                task.setAgentId(agentId);
                ExecutionResult result = executorService.execute(task);

                jdbcTemplate.update(
                    "UPDATE ecos_ai.ecos_evolution_log SET status='COMPLETED', output_result=?::jsonb, completed_at=NOW() WHERE id=?",
                    result.getOutput() != null ? result.getOutput() : "{}", logId
                );
            } catch (Exception e) {
                log.error("Evolution stage {} failed: {}", stage, e.getMessage());
                try {
                    jdbcTemplate.update(
                        "UPDATE ecos_ai.ecos_evolution_log SET status='FAILED', output_result=?::jsonb, completed_at=NOW() WHERE id=?",
                        "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}", logId
                    );
                } catch (Exception ex) {
                    log.warn("Failed to update evolution log status: {}", ex.getMessage());
                }
            }
        }

        return missionId;
    }

    public List<Map<String, Object>> getEvolutionLog(String missionId) {
        return jdbcTemplate.queryForList(
            "SELECT id, mission_id, stage, agent_id, status, started_at, completed_at FROM ecos_ai.ecos_evolution_log WHERE mission_id = ? ORDER BY started_at",
            missionId
        );
    }
}
