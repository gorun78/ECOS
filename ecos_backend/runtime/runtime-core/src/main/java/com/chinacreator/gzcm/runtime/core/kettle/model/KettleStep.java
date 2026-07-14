package com.chinacreator.gzcm.runtime.core.kettle.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Kettle濮濄儵顎冨Ο鈥崇€?
 * 鐞涖劎銇欿ettle鏉烆剚宕叉稉顓犳畱娑撯偓娑擃亝顒炴?
 * 
 * @author CDRC Runtime Team
 */
public class KettleStep {
    
    /**
     * 濮濄儵顎僆D
     */
    private String id;
    
    /**
     * 濮濄儵顎冮崥宥囆?
     */
    private String name;
    
    /**
     * 濮濄儵顎冪猾璇茬€烽敍鍫濐洤TableInput閵嗕箑ableOutput缁涘绱?
     */
    private String type;
    
    /**
     * 濮濄儵顎冮幓蹇氬牚
     */
    private String description;
    
    /**
     * 濮濄儵顎冮柊宥囩枂閸欏倹鏆?
     */
    private Map<String, Object> properties;
    
    /**
     * 濮濄儵顎冩担宥囩枂娣団剝浼?
     */
    private StepPosition position;
    
    /**
     * 濮濄儵顎冮崗鍐╂殶閹?
     */
    private Map<String, Object> metadata;
    
    public KettleStep() {
        this.properties = new HashMap<>();
        this.metadata = new HashMap<>();
    }
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
    
    public void setProperty(String key, Object value) {
        this.properties.put(key, value);
    }
    
    public Object getProperty(String key) {
        return this.properties.get(key);
    }
    
    public StepPosition getPosition() {
        return position;
    }
    
    public void setPosition(StepPosition position) {
        this.position = position;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    /**
     * 濮濄儵顎冩担宥囩枂娣団剝浼?
     */
    public static class StepPosition {
        private int x;
        private int y;
        
        public StepPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
        
        public int getX() {
            return x;
        }
        
        public void setX(int x) {
            this.x = x;
        }
        
        public int getY() {
            return y;
        }
        
        public void setY(int y) {
            this.y = y;
        }
    }
}

