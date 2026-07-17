package com.chinacreator.gzcm.services.agent.runtime.model;

public class Mission {
    private String id;
    private String title;
    private Goal goal;
    private CollaborationMode mode;
    private MissionStatus status = MissionStatus.PENDING;
    private String result;

    public Mission() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Goal getGoal() { return goal; }
    public void setGoal(Goal goal) { this.goal = goal; }
    public CollaborationMode getMode() { return mode; }
    public void setMode(CollaborationMode mode) { this.mode = mode; }
    public MissionStatus getStatus() { return status; }
    public void setStatus(MissionStatus status) { this.status = status; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
