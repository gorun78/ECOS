package com.chinacreator.gzcm.services.agent.runtime.planner;

import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionPlan;
import com.chinacreator.gzcm.services.agent.runtime.model.Goal;

public interface PlannerService {
    ExecutionPlan createPlan(Goal goal);
    ExecutionPlan decompose(ExecutionPlan plan);
}
