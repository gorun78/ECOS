package com.chinacreator.gzcm.sysman.config.git.entity;

import java.util.Date;
import java.util.List;

/**
 * Git提交实体
 * 
 * @author CDRC Design Team
 */
public class GitCommit {
    private String commitId;
    private String shortId;
    private String message;
    private String author;
    private String committer;
    private Date commitTime;
    private List<String> changedFiles;
    
    public String getCommitId() {
        return commitId;
    }
    
    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }
    
    public String getShortId() {
        return shortId;
    }
    
    public void setShortId(String shortId) {
        this.shortId = shortId;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public String getCommitter() {
        return committer;
    }
    
    public void setCommitter(String committer) {
        this.committer = committer;
    }
    
    public Date getCommitTime() {
        return commitTime;
    }
    
    public void setCommitTime(Date commitTime) {
        this.commitTime = commitTime;
    }
    
    public List<String> getChangedFiles() {
        return changedFiles;
    }
    
    public void setChangedFiles(List<String> changedFiles) {
        this.changedFiles = changedFiles;
    }
}


