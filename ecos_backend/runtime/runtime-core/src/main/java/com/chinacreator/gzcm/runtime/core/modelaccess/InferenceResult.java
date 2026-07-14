package com.chinacreator.gzcm.runtime.core.modelaccess;

import java.util.Map;

/**
 * 妯″瀷鎺ㄧ悊缁撴灉銆?
 */
public class InferenceResult {

    private String modelId;
    private String modelVersion;
    private String output;
    private long latency;
    private Map<String, Object> metadata;

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public long getLatency() {
        return latency;
    }

    public void setLatency(long latency) {
        this.latency = latency;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}


