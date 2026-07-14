package com.chinacreator.gzcm.runtime.core.task.model;

import java.util.Date;
import java.util.Map;

/**
 * 浠诲姟鐘舵€?
 * 鐢ㄤ簬鍙嶉浠诲姟鎵ц鐨勭姸鎬佷俊鎭?
 * 
 * @author CDRC Runtime Team
 */
public class TaskStatus {

    /**
     * 浠诲姟ID
     */
    private String taskId;

    /**
     * 浠诲姟鐘舵€佹灇涓?
     */
    private Status status;

    /**
     * 浠诲姟鐘舵€佹弿杩?
     */
    private String statusMessage;

    /**
     * 杩涘害鐧惧垎姣旓紙0-100锛?
     */
    private Integer progress;

    /**
     * 褰撳墠鎵ц鐨勬楠D
     */
    private String currentStepId;

    /**
     * 寮€濮嬫椂闂?
     */
    private Date startTime;

    /**
     * 缁撴潫鏃堕棿
     */
    private Date endTime;

    /**
     * 棰勮鍓╀綑鏃堕棿锛堟绉掞級
     */
    private Long estimatedRemainingTime;

    /**
     * 宸插鐞嗚褰曟暟
     */
    private Long processedRecords;

    /**
     * 鎬昏褰曟暟
     */
    private Long totalRecords;

    /**
     * 閿欒淇℃伅
     */
    private String errorMessage;

    /**
     * 閿欒鍫嗘爤
     */
    private String errorStack;

    /**
     * 鎵ц缁撴灉锛圝SON鏍煎紡锛?
     */
    private String result;

    /**
     * 鎵ц鎸囨爣锛堝锛氬鐞嗛€熷害銆佸唴瀛樹娇鐢ㄧ瓑锛?
     */
    private Map<String, Object> metrics;

    /**
     * 鏇存柊鏃堕棿
     */
    private Date updateTime;

    /**
     * 浠诲姟鐘舵€佹灇涓?
     */
    public enum Status {
        /**
         * 寰呮墽琛?
         */
        PENDING,
        /**
         * 瑙ｆ瀽涓?
         */
        PARSING,
        /**
         * 宸茶В鏋?
         */
        PARSED,
        /**
         * 鎵ц涓?
         */
        RUNNING,
        /**
         * 鏆傚仠
         */
        PAUSED,
        /**
         * 鎴愬姛
         */
        SUCCEEDED,
        /**
         * 澶辫触
         */
        FAILED,
        /**
         * 宸插彇娑?
         */
        CANCELLED,
        /**
         * 瓒呮椂
         */
        TIMEOUT
    }

    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Integer getProgress() {
        return progress != null ? progress : 0;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getCurrentStepId() {
        return currentStepId;
    }

    public void setCurrentStepId(String currentStepId) {
        this.currentStepId = currentStepId;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Long getEstimatedRemainingTime() {
        return estimatedRemainingTime;
    }

    public void setEstimatedRemainingTime(Long estimatedRemainingTime) {
        this.estimatedRemainingTime = estimatedRemainingTime;
    }

    public Long getProcessedRecords() {
        return processedRecords;
    }

    public void setProcessedRecords(Long processedRecords) {
        this.processedRecords = processedRecords;
    }

    public Long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(Long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorStack() {
        return errorStack;
    }

    public void setErrorStack(String errorStack) {
        this.errorStack = errorStack;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}

