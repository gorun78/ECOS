package com.chinacreator.gzcm.services.ontology.model;

import java.util.ArrayList;
import java.util.List;

public class LifecycleDefinition {
    private String initialState;
    private List<String> states = new ArrayList<>();
    private List<TransitionDefinition> transitions = new ArrayList<>();

    public LifecycleDefinition() {}

    public String getInitialState() { return initialState; }
    public void setInitialState(String initialState) { this.initialState = initialState; }
    public List<String> getStates() { return states; }
    public void setStates(List<String> states) { this.states = states; }
    public List<TransitionDefinition> getTransitions() { return transitions; }
    public void setTransitions(List<TransitionDefinition> transitions) { this.transitions = transitions; }
}
