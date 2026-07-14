package com.chinacreator.gzcm.runtime.core.kettle.model;

/**
 * Kettle鏉╃偞甯村Ο鈥崇€?
 * 鐞涖劎銇欿ettle鏉烆剚宕叉稉顓燁劄妤犮倓绠ｉ梻瀵告畱鏉╃偞甯撮崗宕囬兇
 * 
 * @author CDRC Runtime Team
 */
public class KettleHop {
    
    /**
     * 濠ф劖顒炴顥疍
     */
    private String fromStepId;
    
    /**
     * 閻╊喗鐖ｅ銉╊€僆D
     */
    private String toStepId;
    
    /**
     * 鏉╃偞甯寸猾璇茬€烽敍鍫濐洤NORMAL閵嗕笒RROR缁涘绱?
     */
    private String type;
    
    /**
     * 閺勵垰鎯侀崥顖滄暏
     */
    private boolean enabled;
    
    /**
     * 鏉╃偞甯撮弶鈥叉閿涘牆褰查柅澶涚礆
     */
    private String condition;
    
    public KettleHop() {
        this.enabled = true;
        this.type = "NORMAL";
    }
    
    public KettleHop(String fromStepId, String toStepId) {
        this();
        this.fromStepId = fromStepId;
        this.toStepId = toStepId;
    }
    
    // Getters and Setters
    
    public String getFromStepId() {
        return fromStepId;
    }
    
    public void setFromStepId(String fromStepId) {
        this.fromStepId = fromStepId;
    }
    
    public String getToStepId() {
        return toStepId;
    }
    
    public void setToStepId(String toStepId) {
        this.toStepId = toStepId;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getCondition() {
        return condition;
    }
    
    public void setCondition(String condition) {
        this.condition = condition;
    }
}

