package com.chinacreator.gzcm.runtime.core.logging.archive;

import java.util.Date;

public class ArchiveResult {
    private String archiveId;
    private String logType;
    private Date archiveDate;
    private int recordCount;
    private String status;
    private String archiveFilePath;
    private long fileSize;

    public String getArchiveId() {
        return archiveId;
    }
    public void setArchiveId(String archiveId) {
        this.archiveId = archiveId;
    }
    public String getLogType() {
        return logType;
    }
    public void setLogType(String logType) {
        this.logType = logType;
    }
    public Date getArchiveDate() {
        return archiveDate;
    }
    public void setArchiveDate(Date archiveDate) {
        this.archiveDate = archiveDate;
    }
    public Date getArchiveTime() {
        return archiveDate;
    }
    public void setArchiveTime(Date archiveDate) {
        this.archiveDate = archiveDate;
    }
    public int getRecordCount() {
        return recordCount;
    }
    public void setRecordCount(int recordCount) {
        this.recordCount = recordCount;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getArchiveFilePath() {
        return archiveFilePath;
    }
    public void setArchiveFilePath(String archiveFilePath) {
        this.archiveFilePath = archiveFilePath;
    }
    public String getFilePath() {
        return archiveFilePath;
    }
    public void setFilePath(String archiveFilePath) {
        this.archiveFilePath = archiveFilePath;
    }
    public long getFileSize() {
        return fileSize;
    }
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}
