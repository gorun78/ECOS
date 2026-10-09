package com.chinacreator.gzcm.runtime.llm.agent.spi.model;

import java.util.ArrayList;
import java.util.List;

/**
 * SPI 契约：执行计划（接缝义务 1 · planner 能力出参）。
 *
 * <p>{@code status} 用字符串承载 —— 计划/任务状态机的权威归属是 W Agent 编排域（分册 10），
 * 智能域侧只投影不重定义（§8.1 分工口径）。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public class AgentExecutionPlan {

    private String id;
    private String goalId;
    private List<AgentExecutionTask> tasks = new ArrayList<>();
    private String status;

    public AgentExecutionPlan() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGoalId() { return goalId; }
    public void setGoalId(String goalId) { this.goalId = goalId; }
    public List<AgentExecutionTask> getTasks() { return tasks; }
    public void setTasks(List<AgentExecutionTask> tasks) {
        this.tasks = tasks == null ? new ArrayList<>() : tasks;
    }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
