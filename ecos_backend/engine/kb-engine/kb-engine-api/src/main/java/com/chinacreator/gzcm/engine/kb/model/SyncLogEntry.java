package com.chinacreator.gzcm.engine.kb.model;

public class SyncLogEntry {

    private String syncId;
    private String objectType;
    private String operation;
    private String status;
    private String message;
    private String timestamp;

    public SyncLogEntry() {}

    public SyncLogEntry(String syncId, String objectType, String operation, String status, String message, String timestamp) {
        this.syncId = syncId;
        this.objectType = objectType;
        this.operation = operation;
        this.status = status;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getSyncId() { return syncId; }
    public void setSyncId(String syncId) { this.syncId = syncId; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}