package com.chinacreator.gzcm.services.agent.runtime.orchestration;

import com.chinacreator.gzcm.services.agent.runtime.model.CollaborationMode;
import com.chinacreator.gzcm.services.agent.runtime.model.Mission;
import com.chinacreator.gzcm.services.agent.runtime.model.OrchestrationPlan;
import com.chinacreator.gzcm.services.agent.runtime.model.OrchestrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OrchestrationServiceImpl implements OrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(OrchestrationServiceImpl.class);

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
}
