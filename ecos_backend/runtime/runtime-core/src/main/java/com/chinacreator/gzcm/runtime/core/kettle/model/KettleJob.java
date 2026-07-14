package com.chinacreator.gzcm.runtime.core.kettle.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kettle Job濡€崇€?
 * 鐞涖劎銇氭稉鈧稉鐙礶ttle鏉烆剚宕叉担婊€绗?
 * 
 * @author CDRC Runtime Team
 */
public class KettleJob {
    
    /**
     * Job閸氬秶袨
     */
    private String name;
    
    /**
     * Job閹诲繗鍫?
     */
    private String description;
    
    /**
     * Job閸欏倹鏆?
     */
    private Map<String, String> parameters;
    
    /**
     * 鏉烆剚宕插銉╊€冮崚妤勩€?
     */
    private List<KettleStep> steps;
    
    /**
     * 濮濄儵顎冩潻鐐村复閸忓磭閮?
     */
    private List<KettleHop> hops;
    
    /**
     * Job閸忓啯鏆熼幑?
     */
    private Map<String, Object> metadata;
    
    public KettleJob() {
        this.steps = new ArrayList<>();
        this.hops = new ArrayList<>();
    }
    
    // Getters and Setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Map<String, String> getParameters() {
        return parameters;
    }
    
    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }
    
    public List<KettleStep> getSteps() {
        return steps;
    }
    
    public void setSteps(List<KettleStep> steps) {
        this.steps = steps;
    }
    
    public void addStep(KettleStep step) {
        this.steps.add(step);
    }
    
    public List<KettleHop> getHops() {
        return hops;
    }
    
    public void setHops(List<KettleHop> hops) {
        this.hops = hops;
    }
    
    public void addHop(KettleHop hop) {
        this.hops.add(hop);
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}

