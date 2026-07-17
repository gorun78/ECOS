package com.chinacreator.gzcm.services.agent.runtime.planner;

import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionPlan;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask;
import com.chinacreator.gzcm.services.agent.runtime.model.Goal;
import com.chinacreator.gzcm.services.agent.runtime.model.PlanStatus;
import com.chinacreator.gzcm.services.agent.runtime.model.TaskStatus;
import com.chinacreator.gzcm.services.agent.runtime.model.ToolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PlannerServiceImpl implements PlannerService {
    private static final Logger log = LoggerFactory.getLogger(PlannerServiceImpl.class);

    @Override
    public ExecutionPlan createPlan(Goal goal) {
        log.info("Creating plan for goal: {}", goal.getDescription());
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId(UUID.randomUUID().toString());
        plan.setGoalId(goal.getId());
        plan.setStatus(PlanStatus.PLANNING);
        return plan;
    }

    @Override
    public ExecutionPlan decompose(ExecutionPlan plan) {
        log.info("Decomposing plan: {}", plan.getId());
        ExecutionTask task = new ExecutionTask();
        task.setId(UUID.randomUUID().toString());
        task.setPlanId(plan.getId());
        task.setInstruction("Execute goal: " + plan.getGoalId());
        task.setToolType(ToolType.API);
        task.setStatus(TaskStatus.PENDING);
        plan.getTasks().add(task);
        plan.setStatus(PlanStatus.CREATED);
        return plan;
    }
}
