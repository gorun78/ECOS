package com.chinacreator.gzcm.services.agent.runtime.model;

import java.util.ArrayList;
import java.util.List;

public class ExecutionPlan {
    private String id;
    private String goalId;
    private List<ExecutionTask> tasks = new ArrayList<>();
    private PlanStatus status = PlanStatus.CREATED;

    public ExecutionPlan() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGoalId() { return goalId; }
    public void setGoalId(String goalId) { this.goalId = goalId; }
    public List<ExecutionTask> getTasks() { return tasks; }
    public void setTasks(List<ExecutionTask> tasks) { this.tasks = tasks; }
    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }
}
