package com.chinacreator.gzcm.services.agent.runtime.governance;

import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask;
import com.chinacreator.gzcm.services.agent.runtime.model.GovernanceDecision;
import com.chinacreator.gzcm.services.agent.runtime.model.GovernancePolicy;
import java.util.List;

public interface GovernanceService {
    GovernanceDecision check(GovernancePolicy policy, ExecutionTask task);
    List<GovernancePolicy> getPolicies(String agentId);
}
