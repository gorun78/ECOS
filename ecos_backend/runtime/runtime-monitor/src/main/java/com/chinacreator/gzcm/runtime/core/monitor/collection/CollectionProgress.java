package com.chinacreator.gzcm.runtime.core.monitor.collection;

/**
 * 閲囬泦浠诲姟杩涘害淇℃伅銆?
 */
public class CollectionProgress {

    private long startTime;
    private long currentTime;
    private double progress;
    private long totalRecords;
    private long processedRecords;
    private long failedRecords;
    private double recordsPerSecond;

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(long currentTime) {
        this.currentTime = currentTime;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public long getProcessedRecords() {
        return processedRecords;
    }

    public void setProcessedRecords(long processedRecords) {
        this.processedRecords = processedRecords;
    }

    public long getFailedRecords() {
        return failedRecords;
    }

    public void setFailedRecords(long failedRecords) {
        this.failedRecords = failedRecords;
    }

    public double getRecordsPerSecond() {
        return recordsPerSecond;
    }

    public void setRecordsPerSecond(double recordsPerSecond) {
        this.recordsPerSecond = recordsPerSecond;
    }
}


