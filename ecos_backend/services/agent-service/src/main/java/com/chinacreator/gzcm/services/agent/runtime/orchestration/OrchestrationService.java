package com.chinacreator.gzcm.services.agent.runtime.orchestration;

import com.chinacreator.gzcm.services.agent.runtime.model.CollaborationMode;
import com.chinacreator.gzcm.services.agent.runtime.model.Mission;
import com.chinacreator.gzcm.services.agent.runtime.model.OrchestrationPlan;
import com.chinacreator.gzcm.services.agent.runtime.model.OrchestrationResult;

public interface OrchestrationService {
    OrchestrationPlan plan(Mission mission, CollaborationMode mode);
    OrchestrationResult execute(OrchestrationPlan plan);

    /**
     * Execute a Mission directly, routing based on collaboration mode.
     * SUPERVISOR → Hermes delegate_task; PIPELINE/SWARM/DEBATE → plan+execute.
     */
    OrchestrationResult executeMission(Mission mission);
}
