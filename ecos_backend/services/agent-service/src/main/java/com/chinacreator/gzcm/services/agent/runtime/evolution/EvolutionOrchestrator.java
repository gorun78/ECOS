package com.chinacreator.gzcm.services.agent.runtime.evolution;

import com.chinacreator.gzcm.services.agent.dao.AgentEvolutionDao;
import com.chinacreator.gzcm.services.agent.runtime.executor.ExecutorService;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final AgentEvolutionDao evolutionDao;
    private final ExecutorService executorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvolutionOrchestrator(AgentEvolutionDao evolutionDao, ExecutorService executorService) {
        this.evolutionDao = evolutionDao;
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
                evolutionDao.insertEvolutionLog(logId, missionId, stage, agentId, contextJson);
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

                evolutionDao.markEvolutionCompleted(
                    result.getOutput() != null ? result.getOutput() : "{}", logId
                );
            } catch (Exception e) {
                log.error("Evolution stage {} failed: {}", stage, e.getMessage());
                try {
                    evolutionDao.markEvolutionFailed(
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
        return evolutionDao.listEvolutionLog(missionId);
    }
}
