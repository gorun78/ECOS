package com.chinacreator.gzcm.runtime.core.bigdataengine;

/**
 * 寮曟搸杩炴帴淇℃伅銆?
 */
public class EngineConnection {

    private String connectionId;
    private String engineType;
    private String masterUrl;
    private boolean connected;

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

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

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}


