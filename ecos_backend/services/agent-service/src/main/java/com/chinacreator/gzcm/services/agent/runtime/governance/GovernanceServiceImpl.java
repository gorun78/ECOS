package com.chinacreator.gzcm.services.agent.runtime.governance;

import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask;
import com.chinacreator.gzcm.services.agent.runtime.model.GovernanceDecision;
import com.chinacreator.gzcm.services.agent.runtime.model.GovernancePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GovernanceServiceImpl implements GovernanceService {
    private static final Logger log = LoggerFactory.getLogger(GovernanceServiceImpl.class);
    private final ConcurrentHashMap<String, List<GovernancePolicy>> policyStore = new ConcurrentHashMap<>();

    @Override
    public GovernanceDecision check(GovernancePolicy policy, ExecutionTask task) {
        log.info("Checking governance policy {} for task {}", policy.getId(), task.getId());
        if (!policy.isEnabled()) {
            return new GovernanceDecision(true, "Policy disabled");
        }
        return new GovernanceDecision(true, "Policy check passed");
    }

    @Override
    public List<GovernancePolicy> getPolicies(String agentId) {
        return policyStore.getOrDefault(agentId, new ArrayList<>());
    }
}
