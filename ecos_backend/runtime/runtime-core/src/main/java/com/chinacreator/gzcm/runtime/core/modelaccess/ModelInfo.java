package com.chinacreator.gzcm.runtime.core.modelaccess;

import java.util.Map;

/**
 * 妯″瀷鍏冩暟鎹俊鎭€?
 */
public class ModelInfo {

    private String modelId;
    private String name;
    private String type;
    private String version;
    private String status;
    private long loadedTime;
    private Map<String, Object> metadata;

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLoadedTime() {
        return loadedTime;
    }

    public void setLoadedTime(long loadedTime) {
        this.loadedTime = loadedTime;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}


