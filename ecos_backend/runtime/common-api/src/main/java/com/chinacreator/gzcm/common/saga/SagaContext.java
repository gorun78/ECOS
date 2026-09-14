package com.chinacreator.gzcm.common.saga;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SagaContext {
    private final String sagaId;
    private final String sagaType;
    private final Map<String, Object> inputData;
    private final Map<String, Object> compensationData;
    private int currentStep;
    private SagaStatus status;

    public SagaContext(String sagaType, Map<String, Object> inputData) {
        this.sagaId = UUID.randomUUID().toString();
        this.sagaType = sagaType;
        this.inputData = new HashMap<>(inputData);
        this.compensationData = new HashMap<>();
        this.currentStep = 0;
        this.status = SagaStatus.STARTED;
    }

    public String getSagaId() { return sagaId; }
    public String getSagaType() { return sagaType; }
    public Map<String, Object> getInputData() { return inputData; }
    public Map<String, Object> getCompensationData() { return compensationData; }
    public int getCurrentStep() { return currentStep; }
    public void setCurrentStep(int step) { this.currentStep = step; }
    public SagaStatus getStatus() { return status; }
    public void setStatus(SagaStatus status) { this.status = status; }

    public void addCompensationData(String key, Object value) {
        compensationData.put(key, value);
    }

    public Object getInput(String key) {
        return inputData.get(key);
    }

    public enum SagaStatus {
        STARTED, EXECUTING, COMPENSATING, COMPLETED, FAILED, CANCELLED
    }
}
