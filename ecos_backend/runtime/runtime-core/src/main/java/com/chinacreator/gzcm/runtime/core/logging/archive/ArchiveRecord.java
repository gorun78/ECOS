package com.chinacreator.gzcm.runtime.core.logging.archive;

import java.util.Date;

public class ArchiveRecord {
    private String id;
    private String logType;
    private Date archiveTime;
    private String filePath;
    private long fileSize;
    private int recordCount;
    private String status;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public void setArchiveId(String archiveId) {
        this.id = archiveId;
    }
    public String getArchiveId() {
        return id;
    }

    public String getLogType() {
        return logType;
    }
    public void setLogType(String logType) {
        this.logType = logType;
    }
    public Date getArchiveTime() {
        return archiveTime;
    }
    public void setArchiveTime(Date archiveTime) {
        this.archiveTime = archiveTime;
    }
    public void setArchiveDate(Date archiveDate) {
        this.archiveTime = archiveDate;
    }
    public Date getArchiveDate() {
        return archiveTime;
    }

    public String getFilePath() {
        return filePath;
    }
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    public long getFileSize() {
        return fileSize;
    }
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
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
}
