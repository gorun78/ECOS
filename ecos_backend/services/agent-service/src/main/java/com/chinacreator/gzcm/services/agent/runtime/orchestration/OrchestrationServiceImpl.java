package com.chinacreator.gzcm.services.agent.runtime.orchestration;

import com.chinacreator.gzcm.services.agent.runtime.mcp.HermesDelegationAdapter;
import com.chinacreator.gzcm.services.agent.runtime.model.CollaborationMode;
import com.chinacreator.gzcm.services.agent.runtime.model.Mission;
import com.chinacreator.gzcm.services.agent.runtime.model.OrchestrationPlan;
import com.chinacreator.gzcm.services.agent.runtime.model.OrchestrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OrchestrationServiceImpl implements OrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(OrchestrationServiceImpl.class);

    @Autowired(required = false)
    @Qualifier("ecosHermesDelegationAdapter")
    private HermesDelegationAdapter hermesDelegationAdapter;

    @Override
    public OrchestrationPlan plan(Mission mission, CollaborationMode mode) {
        log.info("Planning orchestration for mission: {} mode: {}", mission.getId(), mode);
        OrchestrationPlan plan = new OrchestrationPlan();
        plan.setId(UUID.randomUUID().toString());
        plan.setMissionId(mission.getId());

        switch (mode) {
            case SUPERVISOR:
                plan.getAgentAssignments().put("coordinator", List.of("ag-data", "ag-knowledge", "ag-compliance"));
                plan.setExecutionOrder(List.of("coordinator"));
                break;
            case PIPELINE:
                plan.setExecutionOrder(List.of("ag-data", "ag-knowledge", "ag-compliance"));
                break;
            case SWARM:
                plan.getAgentAssignments().put("participants", List.of("ag-data", "ag-knowledge", "ag-compliance"));
                break;
            case DEBATE:
                plan.getAgentAssignments().put("pro", List.of("ag-data"));
                plan.getAgentAssignments().put("con", List.of("ag-compliance"));
                plan.getAgentAssignments().put("judge", List.of("ag-knowledge"));
                plan.setExecutionOrder(List.of("pro", "con", "judge"));
                break;
        }
        return plan;
    }

    @Override
    public OrchestrationResult execute(OrchestrationPlan plan) {
        log.info("Executing orchestration plan: {}", plan.getId());
        OrchestrationResult result = new OrchestrationResult();
        result.setPlanId(plan.getId());
        result.setMissionId(plan.getMissionId());
        result.setSuccess(true);
        result.setSummary("Orchestration completed");
        return result;
    }

    /**
     * Execute a Mission directly, routing based on collaboration mode.
     *
     * <ul>
     *   <li>SUPERVISOR → delegates to Hermes via delegate_task (if adapter available)</li>
     *   <li>PIPELINE / SWARM / DEBATE → uses existing plan-then-execute flow</li>
     *   <li>Adapter unavailable → falls back to existing logic</li>
     * </ul>
     */
    @Override
    public OrchestrationResult executeMission(Mission mission) {
        CollaborationMode mode = mission.getMode();
        log.info("executeMission: id={}, mode={}, title={}", mission.getId(), mode, mission.getTitle());

        // SUPERVISOR mode: delegate complex task to Hermes
        if (hermesDelegationAdapter != null && CollaborationMode.SUPERVISOR == mode) {
            try {
                String goal = mission.getGoal() != null ? mission.getGoal().getDescription() : mission.getTitle();
                String context = mission.getTitle();
                hermesDelegationAdapter.delegate(goal, context);
                return buildDelegatedResult(mission);
            } catch (Exception e) {
                log.warn("Hermes delegation failed, fallback to local orchestration: {}", e.getMessage());
            }
        }

        // PIPELINE / SWARM / DEBATE (or adapter unavailable): use existing plan+execute flow
        OrchestrationPlan plan = plan(mission, mode);
        return execute(plan);
    }

    /**
     * Build a result indicating the mission was delegated to Hermes.
     */
    private OrchestrationResult buildDelegatedResult(Mission mission) {
        OrchestrationResult result = new OrchestrationResult();
        result.setMissionId(mission.getId());
        result.setSuccess(true);
        result.setSummary("Mission delegated to Hermes (SUPERVISOR mode)");
        return result;
    }
}
