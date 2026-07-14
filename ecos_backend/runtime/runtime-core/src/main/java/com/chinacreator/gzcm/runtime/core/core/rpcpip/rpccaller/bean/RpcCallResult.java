package com.chinacreator.gzcm.runtime.core.core.rpcpip.rpccaller.bean;

import java.io.Serializable;

/**
 * RpcCallResult - RPC调用结果Bean类
 * 用于封装RPC调用的返回结果
 */
public class RpcCallResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private boolean success;
    private String result;
    private String error;
    private String errorMessage;
    private Object data;
    private String nodeId;
    private long executionTime;
    private long total_records;
    private long return_records;
    
    // Getters and setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    /**
     * Alias for isSuccess() for compatibility
     */
    public boolean isSuccessFlag() {
        return success;
    }
    
    public String getResult() {
        return result;
    }
    
    public void setResult(String result) {
        this.result = result;
    }
    
    /**
     * Alias for getResult() for compatibility
     */
    public String getResultMessage() {
        return result;
    }
    
    /**
     * Alias for setSuccess() for compatibility
     */
    public void setSuccessFlag(boolean success) {
        this.success = success;
    }
    
    /**
     * Alias for setResult() for compatibility
     */
    public void setResultMessage(String result) {
        this.result = result;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public long getTotal_records() {
        return total_records;
    }
    
    public void setTotal_records(long total_records) {
        this.total_records = total_records;
    }
    
    public long getReturn_records() {
        return return_records;
    }
    
    public void setReturn_records(long return_records) {
        this.return_records = return_records;
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    public long getExecutionTime() {
        return executionTime;
    }
    
    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }
    
    // Additional field for execute log ID
    private String excute_log_id;
    
    public String getExcute_log_id() {
        return excute_log_id;
    }
    
    public void setExcute_log_id(String excute_log_id) {
        this.excute_log_id = excute_log_id;
    }
}

