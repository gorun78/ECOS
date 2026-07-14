package com.chinacreator.gzcm.runtime.core.bigdataengine;

import java.util.Map;

/**
 * 寮曟搸杩炴帴閰嶇疆銆?
 */
public class EngineConfig {

    private String engineType;
    private String masterUrl;
    private Map<String, String> options;

    public String getEngineType() {
        return engineType;
    }

    public void setEngineType(String engineType) {
        this.engineType = engineType;
    }

    public String getMasterUrl() {
        return masterUrl;
    }

    public void setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }
}


