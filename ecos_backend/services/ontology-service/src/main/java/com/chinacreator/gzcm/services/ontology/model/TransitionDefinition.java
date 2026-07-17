package com.chinacreator.gzcm.services.ontology.model;

public class TransitionDefinition {
    private String from;
    private String to;
    private String trigger;
    private String guard;

    public TransitionDefinition() {}

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }
    public String getGuard() { return guard; }
    public void setGuard(String guard) { this.guard = guard; }
}
