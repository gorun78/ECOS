package com.chinacreator.gzcm.engine.cognitive2.model;

import java.time.Instant;
import java.util.Map;

public class WorldState {
    private String id;
    private Instant timestamp;
    private Map<String, Object> stateData;
    public WorldState() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Map<String, Object> getStateData() { return stateData; }
    public void setStateData(Map<String, Object> stateData) { this.stateData = stateData; }
}
