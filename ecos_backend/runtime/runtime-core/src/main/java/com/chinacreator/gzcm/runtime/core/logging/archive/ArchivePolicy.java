package com.chinacreator.gzcm.runtime.core.logging.archive;

public class ArchivePolicy {
    private boolean compress;
    private int retentionDays;
    private String targetStorage; // e.g., "local", "s3"
    private String path;

    public ArchivePolicy() {
    }

    public ArchivePolicy(String targetStorage, int retentionDays, String path, boolean compress) {
        this.targetStorage = targetStorage;
        this.retentionDays = retentionDays;
        this.path = path;
        this.compress = compress;
    }

    public boolean isCompress() {
        return compress;
    }
    public void setCompress(boolean compress) {
        this.compress = compress;
    }
    public int getRetentionDays() {
        return retentionDays;
    }
    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
    public String getTargetStorage() {
        return targetStorage;
    }
    public void setTargetStorage(String targetStorage) {
        this.targetStorage = targetStorage;
    }
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
}
