package com.chinacreator.gzcm.runtime.core.logging.archive;

public class TieredArchiveStrategy {
    private String warmArchivePath;
    private String coldArchivePath;
    private int warmRetentionDays;
    private int coldRetentionDays;
    private int archiveIntervalDays;

    public TieredArchiveStrategy() {
    }

    public TieredArchiveStrategy(String basePath, int warmRetentionDays, int coldRetentionDays, int archiveIntervalDays) {
        this.warmArchivePath = basePath + "/warm";
        this.coldArchivePath = basePath + "/cold";
        this.warmRetentionDays = warmRetentionDays;
        this.coldRetentionDays = coldRetentionDays;
        this.archiveIntervalDays = archiveIntervalDays;
    }

    public String getWarmArchivePath() {
        return warmArchivePath;
    }

    public void setWarmArchivePath(String warmArchivePath) {
        this.warmArchivePath = warmArchivePath;
    }

    public String getColdArchivePath() {
        return coldArchivePath;
    }

    public void setColdArchivePath(String coldArchivePath) {
        this.coldArchivePath = coldArchivePath;
    }

    public int getWarmRetentionDays() {
        return warmRetentionDays;
    }

    public void setWarmRetentionDays(int warmRetentionDays) {
        this.warmRetentionDays = warmRetentionDays;
    }

    public int getColdRetentionDays() {
        return coldRetentionDays;
    }

    public void setColdRetentionDays(int coldRetentionDays) {
        this.coldRetentionDays = coldRetentionDays;
    }

    public int getArchiveIntervalDays() {
        return archiveIntervalDays;
    }

    public void setArchiveIntervalDays(int archiveIntervalDays) {
        this.archiveIntervalDays = archiveIntervalDays;
    }
}
