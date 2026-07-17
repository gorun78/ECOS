package com.chinacreator.gzcm.services.agent.runtime.orchestration;

import com.chinacreator.gzcm.services.agent.runtime.model.CollaborationMode;
import com.chinacreator.gzcm.services.agent.runtime.model.Mission;
import com.chinacreator.gzcm.services.agent.runtime.model.OrchestrationPlan;
import com.chinacreator.gzcm.services.agent.runtime.model.OrchestrationResult;

public interface OrchestrationService {
    OrchestrationPlan plan(Mission mission, CollaborationMode mode);
    OrchestrationResult execute(OrchestrationPlan plan);
}
